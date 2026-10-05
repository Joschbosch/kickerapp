package zur.koeln.kickertool.adapter.in.rest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import zur.koeln.kickertool.domain.tournament.MatchStatus;
import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.ResultSource;
import zur.koeln.kickertool.domain.tournament.Side;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/** Ausgehende Antworten der REST-API. */
public final class Responses {

    private Responses() {
    }

    public record Me(UUID id, String displayName, @Schema(description = "Hat der Aufrufer die Admin-Rolle?") boolean admin) {
    }

    public record Player(UUID id, String displayName) {
    }

    public record TournamentSummary(UUID id, String name, LocalDate date, TournamentStatus status,
            int participantCount, int roundCount) {
    }

    public record Config(int tableCount, int pointsWin, int pointsDraw, int pointsLoss, int goalLimit,
            int matchMinutes, @Schema(nullable = true) Integer plannedRounds, int randomRounds) {
    }

    public record Participant(Player player, ParticipantStatus status, Instant registeredAt) {
    }

    public record Tournament(UUID id, String name, LocalDate date, TournamentStatus status, Config config,
            List<Participant> participants, int roundCount,
            @Schema(description = "Die aktuelle Runde, null solange noch keine gestartet wurde", nullable = true)
            Round currentRound) {
    }

    public record Round(int number,
            @Schema(description = "Alle Ergebnisse der Runde sind bestätigt") boolean complete,
            @Schema(description = "In der Reihenfolge der Auslosung, also der Warteschlange für die Tische")
            List<Match> matches) {
    }

    public record Match(UUID id, int round,
            @Schema(description = "Position in der Warteschlange der Runde, beginnend bei 1") int position,
            MatchStatus status,
            @Schema(description = "Tischnummer, null solange das Match wartet", nullable = true) Integer table,
            Team teamA, Team teamB,
            @Schema(description = "Eingetragenes oder festgelegtes Ergebnis, null solange keines vorliegt",
                    nullable = true) Result result) {
    }

    public record Team(List<Slot> members) {
    }

    /**
     * Ein Platz im Team. {@code type = PLAYER}: {@code player} ist der echte Spieler. {@code type = DUMMY}:
     * {@code standIn} ist der Turnierteilnehmer, der einspringt, oder {@code null}, wenn ein Dritter spielt.
     * Dummys bekommen keine Punkte.
     */
    public record Slot(
            @Schema(allowableValues = {"PLAYER", "DUMMY"}) String type,
            @Schema(nullable = true) Player player,
            @Schema(nullable = true, description = "Nur bei DUMMY: wer tatsächlich spielt, ohne Punkte zu bekommen")
            Player standIn) {
    }

    /**
     * Ergebnis eines Matches. {@code confirmed = false}: Eintrag wartet auf Bestätigung bzw. Entscheidung des
     * Admins und zählt noch nicht.
     */
    public record Result(int goalsA, int goalsB,
            @Schema(description = "false = wartet auf Bestätigung oder Admin-Entscheid und zählt noch nicht")
            boolean confirmed,
            @Schema(nullable = true) Player enteredBy, @Schema(nullable = true) Side enteredSide,
            @Schema(nullable = true, description = "TEAM = vom Gegnerteam bestätigt, ADMIN = vom Admin festgelegt")
            ResultSource source) {
    }

    public record RankingEntry(int rank, Player player, ParticipantStatus status, int matchesPlayed, int wins,
            int draws, int losses, int goalsFor, int goalsAgainst, int goalDifference, int points) {
    }

    public record Event(String type, @Schema(nullable = true) UUID matchId, Instant occurredAt) {
    }
}
