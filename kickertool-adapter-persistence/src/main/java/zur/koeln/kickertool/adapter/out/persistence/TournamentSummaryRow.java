package zur.koeln.kickertool.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/** Projektion für die Turnierliste. */
record TournamentSummaryRow(UUID id, String name, LocalDate date, TournamentStatus status, long participantCount,
        int roundCount) {
}
