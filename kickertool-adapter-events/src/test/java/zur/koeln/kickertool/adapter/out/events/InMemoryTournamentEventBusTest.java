package zur.koeln.kickertool.adapter.out.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

class InMemoryTournamentEventBusTest {

    private final InMemoryTournamentEventBus bus = new InMemoryTournamentEventBus(Runnable::run);
    private final TournamentId tournament = TournamentId.random();

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private TournamentEvent event(TournamentId id, TournamentEventType type) {
        return new TournamentEvent(id, type, null, Instant.parse("2026-10-05T10:00:00Z"));
    }

    private void publish(InMemoryTournamentEventBus target, int count) {
        for (int i = 0; i < count; i++) {
            target.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        }
    }

    private static List<String> ids(List<TournamentEvent> events) {
        return events.stream().map(TournamentEvent::id).toList();
    }

    // ---------------------------------------------------------------- Verteilen

    @Test
    void deliversOnlyToSubscribersOfTheSameTournament() {
        List<TournamentEvent> mine = new ArrayList<>();
        List<TournamentEvent> other = new ArrayList<>();
        bus.subscribe(tournament, mine::add);
        bus.subscribe(TournamentId.random(), other::add);

        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));

        assertThat(mine).hasSize(1);
        assertThat(other).isEmpty();
    }

    @Test
    void stopsDeliveringAfterUnsubscribe() {
        List<TournamentEvent> received = new ArrayList<>();
        EventSubscription subscription = bus.subscribe(tournament, received::add);

        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        subscription.close();
        bus.publish(event(tournament, TournamentEventType.RANKING_CHANGED));

        assertThat(received).extracting(TournamentEvent::type).containsExactly(TournamentEventType.MATCHES_CHANGED);
    }

    @Test
    void failingListenerDoesNotAffectOthers() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, e -> {
            throw new IllegalStateException("Client weg");
        });
        bus.subscribe(tournament, received::add);

        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));

        assertThat(received).hasSize(1);
    }

    @Test
    void eventsInsideATransactionAreDeliveredOnlyAfterCommit() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, received::add);

        TransactionSynchronizationManager.initSynchronization();
        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        assertThat(received).as("vor dem Commit").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(received).as("nach dem Commit").hasSize(1);
    }

    @Test
    void eventsOfARolledBackTransactionAreNeverDelivered() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, received::add);

        TransactionSynchronizationManager.initSynchronization();
        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(received).isEmpty();
    }

    // ---------------------------------------------------------------- Kennungen

    @Test
    void numbersEventsPerTournamentStartingAtOne() {
        TournamentId other = TournamentId.random();
        List<TournamentEvent> mine = new ArrayList<>();
        List<TournamentEvent> theirs = new ArrayList<>();
        bus.subscribe(tournament, mine::add);
        bus.subscribe(other, theirs::add);

        publish(bus, 3);
        bus.publish(event(other, TournamentEventType.ROUND_STARTED));

        assertThat(ids(mine)).containsExactly("test-1", "test-2", "test-3");
        assertThat(ids(theirs)).containsExactly("test-1");
    }

    @Test
    void aRolledBackTransactionDoesNotUseUpANumber() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, received::add);

        TransactionSynchronizationManager.initSynchronization();
        bus.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        publish(bus, 1);

        assertThat(ids(received)).containsExactly("test-1");
    }

    @Test
    void keepsPayloadOfTheEvent() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, received::add);
        MatchId match = MatchId.random();

        bus.publish(new TournamentEvent(tournament, TournamentEventType.MATCHES_CHANGED, match,
                Instant.parse("2026-10-05T10:00:00Z")));

        assertThat(received.get(0).matchId()).isEqualTo(match);
        assertThat(received.get(0).occurredAt()).isEqualTo(Instant.parse("2026-10-05T10:00:00Z"));
    }

    // ---------------------------------------------------------------- Nachliefern

    @Test
    void aNewConnectionGetsNoOldEvents() {
        publish(bus, 3);

        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, null, received::add);

        assertThat(received).isEmpty();
    }

    @Test
    void aReconnectingClientGetsExactlyTheMissedEventsAndThenLiveOnes() {
        publish(bus, 3);

        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, "test-1", received::add);
        assertThat(ids(received)).as("verpasst: 2 und 3").containsExactly("test-2", "test-3");

        publish(bus, 1);
        assertThat(ids(received)).as("danach live, ohne Doppelte").containsExactly("test-2", "test-3", "test-4");
    }

    @Test
    void anUpToDateClientGetsNothingReplayed() {
        publish(bus, 2);

        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, "test-2", received::add);

        assertThat(received).isEmpty();
    }

    @Test
    void asksForAFullReloadWhenTheIdComesFromAnotherStart() {
        publish(bus, 2);

        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, "anderer-start-1", received::add);

        assertThat(received).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(TournamentEventType.RESYNC);
            assertThat(e.id()).as("trägt die aktuelle Kennung").isEqualTo("test-2");
        });
    }

    @Test
    void asksForAFullReloadWhenTheIdIsGarbageOrFromTheFuture() {
        publish(bus, 2);

        for (String lastEventId : new String[] {"quatsch", "test-abc", "-5", "test-99", "test--1"}) {
            List<TournamentEvent> received = new ArrayList<>();
            bus.subscribe(tournament, lastEventId, received::add);
            assertThat(received).as(lastEventId).extracting(TournamentEvent::type)
                    .containsExactly(TournamentEventType.RESYNC);
        }
    }

    @Test
    void asksForAFullReloadForAnUnknownTournament() {
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(TournamentId.random(), "test-4", received::add);

        assertThat(received).extracting(TournamentEvent::type).containsExactly(TournamentEventType.RESYNC);
        assertThat(received.get(0).id()).isEqualTo("test-0");
    }

    @Test
    void asksForAFullReloadWhenTheGapIsBiggerThanTheStoredHistory() {
        InMemoryTournamentEventBus small = new InMemoryTournamentEventBus(Runnable::run, 3, "test");
        publish(small, 5); // gespeichert sind 3, 4 und 5

        List<TournamentEvent> tooOld = new ArrayList<>();
        small.subscribe(tournament, "test-1", tooOld::add);
        assertThat(tooOld).extracting(TournamentEvent::type).containsExactly(TournamentEventType.RESYNC);

        List<TournamentEvent> justInTime = new ArrayList<>();
        small.subscribe(tournament, "test-2", justInTime::add);
        assertThat(ids(justInTime)).containsExactly("test-3", "test-4", "test-5");
    }

    @Test
    void afterAResyncTheClientContinuesWithoutNewResyncs() {
        publish(bus, 2);
        List<TournamentEvent> received = new ArrayList<>();
        bus.subscribe(tournament, "anderer-start-7", received::add);
        String idFromResync = received.get(0).id();

        List<TournamentEvent> later = new ArrayList<>();
        publish(bus, 1);
        bus.subscribe(tournament, idFromResync, later::add);

        assertThat(ids(later)).containsExactly("test-3");
    }

    @Test
    void aSubscriptionClosedBeforeItWasAttachedNeverReceivesAnything() {
        List<Runnable> queue = new ArrayList<>();
        Executor manual = queue::add;
        InMemoryTournamentEventBus queued = new InMemoryTournamentEventBus(manual);
        List<TournamentEvent> received = new ArrayList<>();

        EventSubscription subscription = queued.subscribe(tournament, "test-0", received::add);
        subscription.close();
        queued.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));
        queue.forEach(Runnable::run);

        assertThat(received).isEmpty();
    }

    @Test
    void replayAndLiveEventsKeepTheirOrderOnTheDeliveryThread() {
        List<Runnable> queue = new ArrayList<>();
        InMemoryTournamentEventBus queued = new InMemoryTournamentEventBus(queue::add);
        List<TournamentEvent> received = new ArrayList<>();

        queued.publish(event(tournament, TournamentEventType.MATCHES_CHANGED));      // Aufgabe 1: vergibt test-1
        queued.subscribe(tournament, "test-0", received::add);                        // Aufgabe 2: hängt an, liefert test-1 nach
        queued.publish(event(tournament, TournamentEventType.RANKING_CHANGED));       // Aufgabe 3: test-2 live
        queue.forEach(Runnable::run);

        assertThat(ids(received)).containsExactly("test-1", "test-2");
    }
}
