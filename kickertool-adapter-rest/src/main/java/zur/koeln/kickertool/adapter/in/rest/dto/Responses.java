package zur.koeln.kickertool.adapter.in.rest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import zur.koeln.kickertool.domain.tournament.MatchStatus;
import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.ResultSource;
import zur.koeln.kickertool.domain.tournament.Side;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/** Ausgehende Antworten der REST-API. */
public final class Responses {

    private Responses() {
    }

    public record Me(UUID id, String displayName, boolean admin) {
    }

    public record Player(UUID id, String displayName) {
    }

    public record TournamentSummary(UUID id, String name, LocalDate date, TournamentStatus status,
            int participantCount, int roundCount) {
    }

    public record Config(int tableCount, int pointsWin, int pointsDraw, int pointsLoss, int goalLimit,
            int matchMinutes, Integer plannedRounds, int randomRounds) {
    }

    public record Participant(Player player, ParticipantStatus status, Instant registeredAt) {
    }

    public record Tournament(UUID id, String name, LocalDate date, TournamentStatus status, Config config,
            List<Participant> participants, int roundCount, Round currentRound) {
    }

    public record Round(int number, boolean complete, List<Match> matches) {
    }

    public record Match(UUID id, int round, int position, MatchStatus status, Integer table, Team teamA, Team teamB,
            Result result) {
    }

    public record Team(List<Slot> members) {
    }

    /**
     * Ein Platz im Team. {@code type = PLAYER}: {@code player} ist der echte Spieler. {@code type = DUMMY}:
     * {@code standIn} ist der Turnierteilnehmer, der einspringt, oder {@code null}, wenn ein Dritter spielt.
     * Dummys bekommen keine Punkte.
     */
    public record Slot(String type, Player player, Player standIn) {
    }

    /**
     * Ergebnis eines Matches. {@code confirmed = false}: Eintrag wartet auf Bestätigung bzw. Entscheidung des
     * Admins und zählt noch nicht.
     */
    public record Result(int goalsA, int goalsB, boolean confirmed, Player enteredBy, Side enteredSide,
            ResultSource source) {
    }

    public record RankingEntry(int rank, Player player, ParticipantStatus status, int matchesPlayed, int wins,
            int draws, int losses, int goalsFor, int goalsAgainst, int goalDifference, int points) {
    }

    public record Event(String type, UUID matchId, Instant occurredAt) {
    }
}
