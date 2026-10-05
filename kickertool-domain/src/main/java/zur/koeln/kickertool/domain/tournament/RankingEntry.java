package zur.koeln.kickertool.domain.tournament;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Eine Zeile der Rangliste. Spieler mit gleichen Werten teilen sich den Rang. */
public record RankingEntry(
        PlayerId playerId,
        ParticipantStatus status,
        int rank,
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
