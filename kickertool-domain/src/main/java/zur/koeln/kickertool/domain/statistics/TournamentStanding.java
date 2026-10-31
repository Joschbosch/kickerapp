package zur.koeln.kickertool.domain.statistics;

import java.time.LocalDate;

import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/**
 * Der Stand eines Spielers in einem Turnier. Im laufenden Turnier ist es der aktuelle Stand, im beendeten der
 * Endstand.
 */
public record TournamentStanding(
        TournamentId tournamentId,
        String name,
        LocalDate date,
        TournamentStatus tournamentStatus,
        ParticipantStatus participantStatus,
        int rank,
        int participantCount,
        int matchesPlayed,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int points) {

    public int goalDifference() {
        return goalsFor - goalsAgainst;
    }
}
