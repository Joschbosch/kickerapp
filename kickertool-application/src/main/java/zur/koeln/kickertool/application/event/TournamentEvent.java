package zur.koeln.kickertool.application.event;

import java.time.Instant;

import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Hinweis an Clients, dass sich etwas geändert hat. Die Events tragen keine Daten, Clients laden den neuen Stand über
 * die Abfrage-Endpunkte.
 *
 * @param matchId betroffenes Match, falls das Event sich auf ein einzelnes Match bezieht, sonst {@code null}
 * @param id      Kennung für das Nachliefern verpasster Events (undurchsichtig, wächst je Turnier). Vergibt der
 *                Event-Verteiler beim Ausliefern, beim Erzeugen ist sie {@code null}.
 */
public record TournamentEvent(TournamentId tournamentId, TournamentEventType type, MatchId matchId,
        Instant occurredAt, String id) {

    public TournamentEvent(TournamentId tournamentId, TournamentEventType type, MatchId matchId, Instant occurredAt) {
        this(tournamentId, type, matchId, occurredAt, null);
    }

    public TournamentEvent withId(String newId) {
        return new TournamentEvent(tournamentId, type, matchId, occurredAt, newId);
    }
}
