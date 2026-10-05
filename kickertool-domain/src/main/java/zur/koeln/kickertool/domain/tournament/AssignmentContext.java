package zur.koeln.kickertool.domain.tournament;

import java.util.List;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Eingaben für die Teamzuteilung einer neuen Runde.
 *
 * @param roundNumber    Nummer der neuen Runde, beginnend bei 1
 * @param players        alle aktiven Spieler, sortiert nach aktuellem Rang (bei gleichem Rang nach Anmeldung)
 * @param ranking        aktuelle Rangliste
 * @param previousRounds alle bisherigen Runden, z. B. um Partner-Wiederholungen zu vermeiden
 * @param config         aktuelle Konfiguration des Turniers (Tischzahl, Zahl der Zufallsrunden, ...)
 */
public record AssignmentContext(
        int roundNumber,
        List<PlayerId> players,
        Ranking ranking,
        List<Round> previousRounds,
        TournamentConfig config) {

    public AssignmentContext {
        players = List.copyOf(players);
        previousRounds = List.copyOf(previousRounds);
    }
}
