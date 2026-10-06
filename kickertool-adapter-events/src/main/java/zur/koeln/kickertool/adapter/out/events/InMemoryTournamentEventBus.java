package zur.koeln.kickertool.adapter.out.events;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Verteilt Events innerhalb dieser Anwendungsinstanz. Events, die in einer Transaktion veröffentlicht werden, gehen
 * erst nach dem Commit raus (bei Rollback gar nicht). Die Auslieferung läuft auf einem eigenen Thread, damit ein
 * langsamer Client keine Anfrage aufhält und die Reihenfolge der Events erhalten bleibt.
 *
 * <p><b>Kennungen und Nachliefern:</b> Jedes Event bekommt beim Ausliefern die Kennung {@code <Start>-<Nummer>}. Die
 * Nummer zählt je Turnier hoch, der Start-Teil ändert sich bei jedem Neustart der App. Die letzten Events je Turnier
 * (Standard 200) bleiben im Speicher. Verbindet sich ein Client mit der Kennung seines letzten Events neu, bekommt er
 * die verpassten Events nachgeliefert. Passt die Kennung nicht mehr (anderer Start, zu lange weg, unbekannt), bekommt er
 * stattdessen ein einzelnes {@link TournamentEventType#RESYNC} und muss alles neu laden.
 *
 * <p>Gilt nur für eine einzelne Instanz. Für mehrere Instanzen wird dieser Adapter durch einen anderen ersetzt, der z. B.
 * über Redis oder PostgreSQL {@code LISTEN/NOTIFY} verteilt.
 */
public class InMemoryTournamentEventBus implements TournamentEventBus, AutoCloseable {

    /** So viele Events je Turnier bleiben zum Nachliefern vorrätig. */
    static final int DEFAULT_HISTORY_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(InMemoryTournamentEventBus.class);

    private final Object lock = new Object();
    private final Map<TournamentId, Stream> streams = new HashMap<>();
    private final Map<TournamentId, List<Subscriber>> subscribers = new HashMap<>();
    private final Executor deliveryExecutor;
    private final ExecutorService ownedExecutor;
    private final int historySize;
    private final String epoch;

    public InMemoryTournamentEventBus() {
        this.ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tournament-event-delivery");
            thread.setDaemon(true);
            return thread;
        });
        this.deliveryExecutor = ownedExecutor;
        this.historySize = DEFAULT_HISTORY_SIZE;
        this.epoch = Long.toString(System.currentTimeMillis(), 36);
    }

    /**
     * Für Tests. Der Executor muss die Aufgaben nacheinander ausführen (z. B. {@code Runnable::run}), sonst ist die
     * Reihenfolge der Events nicht gesichert.
     */
    InMemoryTournamentEventBus(Executor deliveryExecutor, int historySize, String epoch) {
        this.ownedExecutor = null;
        this.deliveryExecutor = deliveryExecutor;
        this.historySize = historySize;
        this.epoch = epoch;
    }

    InMemoryTournamentEventBus(Executor deliveryExecutor) {
        this(deliveryExecutor, DEFAULT_HISTORY_SIZE, "test");
    }

    @Override
    public void publish(TournamentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(event);
                }
            });
        } else {
            enqueue(event);
        }
    }

    @Override
    public EventSubscription subscribe(TournamentId tournamentId, String lastEventId,
            Consumer<TournamentEvent> listener) {
        Subscriber subscriber = new Subscriber(tournamentId, listener);
        // Auf demselben Thread wie die Auslieferung: Nachgeliefertes und Neues können sich nicht überholen
        deliveryExecutor.execute(() -> attach(subscriber, lastEventId));
        return subscriber::close;
    }

    // ---------------------------------------------------------------- Auslieferung

    private void enqueue(TournamentEvent event) {
        deliveryExecutor.execute(() -> dispatch(event));
    }

    /** Vergibt die Kennung, merkt das Event für späteres Nachliefern vor und gibt es an alle Abonnenten weiter. */
    private void dispatch(TournamentEvent raw) {
        TournamentEvent event;
        List<Subscriber> targets;
        synchronized (lock) {
            Stream stream = streams.computeIfAbsent(raw.tournamentId(), id -> new Stream());
            stream.sequence++;
            event = raw.withId(epoch + "-" + stream.sequence);
            stream.history.addLast(new Entry(stream.sequence, event));
            while (stream.history.size() > historySize) {
                stream.history.removeFirst();
            }
            targets = List.copyOf(subscribers.getOrDefault(raw.tournamentId(), List.of()));
        }
        targets.forEach(subscriber -> subscriber.deliver(event));
    }

    private void attach(Subscriber subscriber, String lastEventId) {
        List<TournamentEvent> missed;
        synchronized (lock) {
            if (subscriber.closed) {
                return;
            }
            missed = missedEvents(subscriber.tournamentId, lastEventId);
            subscribers.computeIfAbsent(subscriber.tournamentId, id -> new ArrayList<>()).add(subscriber);
        }
        missed.forEach(subscriber::deliver);
    }

    /** Was der Client seit {@code lastEventId} verpasst hat. Nur mit gehaltenem {@code lock} aufrufen. */
    private List<TournamentEvent> missedEvents(TournamentId tournamentId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return List.of();
        }
        Stream stream = streams.get(tournamentId);
        long current = stream == null ? 0 : stream.sequence;
        long last = parseSequence(lastEventId);
        if (last < 0 || last > current) {
            return List.of(resync(tournamentId, current));
        }
        if (last == current) {
            return List.of();
        }
        long oldestKept = stream.history.getFirst().sequence;
        if (last + 1 < oldestKept) {
            return List.of(resync(tournamentId, current));
        }
        return stream.history.stream().filter(entry -> entry.sequence > last).map(Entry::event).toList();
    }

    /** Die Nummer aus der Kennung, oder -1, wenn sie von einem anderen Start stammt oder ungültig ist. */
    private long parseSequence(String eventId) {
        int dash = eventId.lastIndexOf('-');
        if (dash <= 0 || !eventId.substring(0, dash).equals(epoch)) {
            return -1;
        }
        try {
            return Long.parseLong(eventId.substring(dash + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Trägt die aktuelle Kennung, damit der Client danach wieder an der richtigen Stelle steht. */
    private TournamentEvent resync(TournamentId tournamentId, long current) {
        return new TournamentEvent(tournamentId, TournamentEventType.RESYNC, null, Instant.now(), epoch + "-" + current);
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }

    // ---------------------------------------------------------------- Hilfsklassen

    private record Entry(long sequence, TournamentEvent event) {
    }

    private static final class Stream {
        private long sequence;
        private final ArrayDeque<Entry> history = new ArrayDeque<>();
    }

    private final class Subscriber {
        private final TournamentId tournamentId;
        private final Consumer<TournamentEvent> listener;
        private volatile boolean closed;

        private Subscriber(TournamentId tournamentId, Consumer<TournamentEvent> listener) {
            this.tournamentId = tournamentId;
            this.listener = listener;
        }

        private void deliver(TournamentEvent event) {
            if (closed) {
                return;
            }
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // z. B. Client hat die Verbindung geschlossen: den Rest nicht beeinträchtigen
                log.debug("Event-Listener für Turnier {} ist fehlgeschlagen: {}", tournamentId, e.toString());
            }
        }

        private void close() {
            synchronized (lock) {
                closed = true;
                List<Subscriber> list = subscribers.get(tournamentId);
                if (list != null) {
                    list.remove(this);
                    if (list.isEmpty()) {
                        subscribers.remove(tournamentId);
                    }
                }
            }
        }
    }
}
