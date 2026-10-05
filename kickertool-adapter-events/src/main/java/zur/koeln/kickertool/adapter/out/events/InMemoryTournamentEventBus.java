package zur.koeln.kickertool.adapter.out.events;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Verteilt Events innerhalb dieser Anwendungsinstanz. Events, die in einer Transaktion veröffentlicht
 * werden, gehen erst nach dem Commit raus (bei Rollback gar nicht). Die Auslieferung läuft auf einem eigenen
 * Thread, damit ein langsamer Client keine Anfrage aufhält und die Reihenfolge der Events erhalten bleibt.
 *
 * <p>Gilt nur für eine einzelne Instanz. Für mehrere Instanzen wird dieser Adapter durch einen anderen
 * ersetzt, der z. B. über Redis oder einen Broker verteilt.
 */
public class InMemoryTournamentEventBus implements TournamentEventBus, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTournamentEventBus.class);

    private final ConcurrentHashMap<TournamentId, List<Consumer<TournamentEvent>>> listeners =
            new ConcurrentHashMap<>();
    private final Executor deliveryExecutor;
    private final ExecutorService ownedExecutor;

    public InMemoryTournamentEventBus() {
        this.ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tournament-event-delivery");
            thread.setDaemon(true);
            return thread;
        });
        this.deliveryExecutor = ownedExecutor;
    }

    /** Für Tests: eigener Executor, z. B. {@code Runnable::run} für synchrone Auslieferung. */
    InMemoryTournamentEventBus(Executor deliveryExecutor) {
        this.ownedExecutor = null;
        this.deliveryExecutor = deliveryExecutor;
    }

    @Override
    public void publish(TournamentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(event);
                }
            });
        } else {
            deliver(event);
        }
    }

    @Override
    public EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener) {
        List<Consumer<TournamentEvent>> list = listeners.computeIfAbsent(tournamentId, id -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(tournamentId, list);
            }
        };
    }

    private void deliver(TournamentEvent event) {
        List<Consumer<TournamentEvent>> subscribed = listeners.get(event.tournamentId());
        if (subscribed == null || subscribed.isEmpty()) {
            return;
        }
        deliveryExecutor.execute(() -> {
            for (Consumer<TournamentEvent> listener : subscribed) {
                try {
                    listener.accept(event);
                } catch (RuntimeException e) {
                    // z. B. Client hat die Verbindung geschlossen: den Rest nicht beeinträchtigen
                    log.debug("Event-Listener für Turnier {} ist fehlgeschlagen: {}", event.tournamentId(), e.toString());
                }
            }
        });
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }
}
