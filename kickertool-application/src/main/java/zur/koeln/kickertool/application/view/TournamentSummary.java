package zur.koeln.kickertool.application.view;

import java.time.LocalDate;

import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/** Kurzform eines Turniers für Listen. */
public record TournamentSummary(
        TournamentId id,
        String name,
        LocalDate date,
        TournamentStatus status,
        int participantCount,
        int roundCount) {
}
