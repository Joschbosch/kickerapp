package zur.koeln.kickertool.adapter.out.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.out.EventSubscription;
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
}
