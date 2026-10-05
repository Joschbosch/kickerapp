package zur.koeln.kickertool.adapter.in.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;

/** Eingehende Anfragen der REST-API. */
public final class Requests {

    private Requests() {
    }

    public record PlanTournament(
            @Schema(example = "Sommerturnier") @NotBlank String name,
            @Schema(example = "2026-10-05") @NotNull LocalDate date,
            @Schema(description = "Optional, ohne Angabe gelten die Standardwerte") @Valid Config config) {
    }

    public record UpdateTournament(
            @Schema(example = "Sommerturnier") @NotBlank String name,
            @Schema(example = "2026-10-05") @NotNull LocalDate date) {
    }

    /** Vollständige Konfiguration. {@code plannedRounds = null} bedeutet: Rundenzahl offen. */
    public record Config(
            @Schema(description = "Anzahl verfügbarer Tische, gilt ab der nächsten Welle", example = "4")
            @NotNull @Min(1) Integer tableCount,
            @Schema(description = "Punkte für einen Sieg", example = "3") @NotNull @Min(0) Integer pointsWin,
            @Schema(description = "Punkte für ein Unentschieden", example = "1") @NotNull @Min(0) Integer pointsDraw,
            @Schema(description = "Punkte für eine Niederlage", example = "0") @NotNull @Min(0) Integer pointsLoss,
            @Schema(description = "Ein Match endet bei dieser Torzahl (ein Satz)", example = "10")
            @NotNull @Min(1) Integer goalLimit,
            @Schema(description = "Maximale Spielzeit in Minuten", example = "5") @NotNull @Min(1) Integer matchMinutes,
            @Schema(description = "Geplante Rundenzahl, nur zur Anzeige. null = offen", nullable = true, example = "8")
            @Min(1) Integer plannedRounds,
            @Schema(description = "Die ersten Runden, die komplett zufällig ausgelost werden. Danach wird nach "
                    + "Rangliste ausgelost (Schweizer DYP).", example = "2")
            @NotNull @Min(0) Integer randomRounds) {

        public TournamentConfig toDomain() {
            return new TournamentConfig(tableCount, pointsWin, pointsDraw, pointsLoss, goalLimit, matchMinutes,
                    plannedRounds, randomRounds);
        }
    }

    @Schema(description = "Anmeldung. Ohne playerId meldet sich der Aufrufer selbst an.")
    public record Register(
            @Schema(description = "Nur für Admins: ID eines anderen bekannten Spielers", nullable = true)
            UUID playerId) {
    }

    public record ChangeParticipantStatus(
            @Schema(description = "PAUSED = ab nächster Runde pausieren, ACTIVE = wieder einsteigen, "
                    + "WITHDRAWN = ausscheiden (endgültig)")
            @NotNull ParticipantStatus status) {
    }

    @Schema(description = "Endstand eines Matches aus Sicht von Team A und Team B, wie in der Antwort des Matches")
    public record Result(
            @Schema(description = "Tore von Team A", example = "10") @NotNull @Min(0) Integer goalsA,
            @Schema(description = "Tore von Team B", example = "7") @NotNull @Min(0) Integer goalsB) {

        public MatchResult toDomain() {
            return new MatchResult(goalsA, goalsB);
        }
    }
}
