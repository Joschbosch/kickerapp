package zur.koeln.kickertool.adapter.in.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

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
            @NotBlank String name,
            @NotNull LocalDate date,
            /** Optional, ohne Angabe gelten die Standardwerte. */
            @Valid Config config) {
    }

    public record UpdateTournament(@NotBlank String name, @NotNull LocalDate date) {
    }

    /**
     * Vollständige Konfiguration. {@code plannedRounds = null} bedeutet: Rundenzahl offen.
     * {@code randomRounds}: Anzahl der ersten Runden, die komplett zufällig ausgelost werden.
     */
    public record Config(
            @NotNull @Min(1) Integer tableCount,
            @NotNull @Min(0) Integer pointsWin,
            @NotNull @Min(0) Integer pointsDraw,
            @NotNull @Min(0) Integer pointsLoss,
            @NotNull @Min(1) Integer goalLimit,
            @NotNull @Min(1) Integer matchMinutes,
            @Min(1) Integer plannedRounds,
            @NotNull @Min(0) Integer randomRounds) {

        public TournamentConfig toDomain() {
            return new TournamentConfig(tableCount, pointsWin, pointsDraw, pointsLoss, goalLimit, matchMinutes,
                    plannedRounds, randomRounds);
        }
    }

    /** Anmeldung. Ohne {@code playerId} meldet sich der Aufrufer selbst an. */
    public record Register(UUID playerId) {
    }

    public record ChangeParticipantStatus(@NotNull ParticipantStatus status) {
    }

    public record Result(@NotNull @Min(0) Integer goalsA, @NotNull @Min(0) Integer goalsB) {

        public MatchResult toDomain() {
            return new MatchResult(goalsA, goalsB);
        }
    }
}
