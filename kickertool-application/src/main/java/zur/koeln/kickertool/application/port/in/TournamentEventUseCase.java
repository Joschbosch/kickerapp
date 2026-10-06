package zur.koeln.kickertool.application.port.in;

import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Live-Updates: Clients lassen sich über Änderungen eines Turniers benachrichtigen. */
public interface TournamentEventUseCase {

    /**
     * Abonniert die Events eines Turniers, auf Wunsch mit Nachliefern verpasster Events.
     *
     * @param lastEventId Kennung des letzten empfangenen Events oder {@code null} bei einer neuen Verbindung
     */
    EventSubscription subscribe(TournamentId tournamentId, String lastEventId, Consumer<TournamentEvent> listener);

    default EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener) {
        return subscribe(tournamentId, null, listener);
    }

    /** Stellt sicher, dass es das Turnier gibt (sonst {@code NotFoundException}). */
    void requireTournament(TournamentId tournamentId);
}
