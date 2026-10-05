package zur.koeln.kickertool.application.event;

import java.time.Instant;

import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Hinweis an Clients, dass sich etwas geändert hat. Die Events tragen keine Daten, Clients laden den neuen
 * Stand über die Abfrage-Endpunkte.
 *
 * @param matchId betroffenes Match, falls das Event sich auf ein einzelnes Match bezieht, sonst {@code null}
 */
public record TournamentEvent(TournamentId tournamentId, TournamentEventType type, MatchId matchId,
        Instant occurredAt) {
}
