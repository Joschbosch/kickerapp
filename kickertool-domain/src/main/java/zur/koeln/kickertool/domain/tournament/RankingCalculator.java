package zur.koeln.kickertool.domain.tournament;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Berechnet die Einzelrangliste aus den bestätigten Matches. Sortierung: Punkte, dann Tordifferenz, dann
 * erzielte Tore. Bei völligem Gleichstand teilen sich Spieler den Rang, die Reihenfolge ist dann die der
 * Anmeldung. Dummys zählen nicht, Ausgeschiedene bleiben mit ihren Punkten enthalten.
 */
final class RankingCalculator {

    private RankingCalculator() {
    }

    static Ranking calculate(TournamentConfig config, Collection<Participant> participants, List<Round> rounds) {
        Map<PlayerId, Stats> stats = new LinkedHashMap<>();
        for (Participant participant : participants) {
            stats.put(participant.playerId(), new Stats(participant.status()));
        }

        for (Round round : rounds) {
            for (Match match : round.matches()) {
                if (!match.isConfirmed()) {
                    continue;
                }
                MatchResult result = match.result().orElseThrow();
                record(stats, match.teamA(), result, Side.A, config);
                record(stats, match.teamB(), result, Side.B, config);
            }
        }

        List<Map.Entry<PlayerId, Stats>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort(Map.Entry.comparingByValue(
                Comparator.comparingInt(Stats::points).reversed()
                        .thenComparing(Comparator.comparingInt(Stats::goalDifference).reversed())
                        .thenComparing(Comparator.comparingInt(Stats::goalsFor).reversed())));

        List<RankingEntry> entries = new ArrayList<>(sorted.size());
        Stats previous = null;
        int previousRank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Stats current = sorted.get(i).getValue();
            int rank = previous != null && current.sameStandingAs(previous) ? previousRank : i + 1;
            entries.add(new RankingEntry(sorted.get(i).getKey(), current.status, rank, current.played, current.wins,
                    current.draws, current.losses, current.goalsFor, current.goalsAgainst, current.points));
            previous = current;
            previousRank = rank;
        }
        return new Ranking(entries);
    }

    private static void record(Map<PlayerId, Stats> stats, Team team, MatchResult result, Side side,
            TournamentConfig config) {
        Outcome outcome = result.winner().map(winner -> winner == side ? Outcome.WIN : Outcome.LOSS)
                .orElse(Outcome.DRAW);
        for (PlayerId playerId : team.realPlayers()) {
            Stats s = stats.get(playerId);
            if (s != null) {
                s.add(result.goalsFor(side), result.goalsAgainst(side), outcome, config);
            }
        }
    }

    private enum Outcome { WIN, DRAW, LOSS }

    private static final class Stats {
        private final ParticipantStatus status;
        private int played;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private int points;

        private Stats(ParticipantStatus status) {
            this.status = status;
        }

        private void add(int scored, int conceded, Outcome outcome, TournamentConfig config) {
            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            switch (outcome) {
                case WIN -> {
                    wins++;
                    points += config.pointsWin();
                }
                case DRAW -> {
                    draws++;
                    points += config.pointsDraw();
                }
                case LOSS -> {
                    losses++;
                    points += config.pointsLoss();
                }
            }
        }

        private int points() {
            return points;
        }

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }

        private int goalsFor() {
            return goalsFor;
        }

        private boolean sameStandingAs(Stats other) {
            return points == other.points && goalDifference() == other.goalDifference()
                    && goalsFor == other.goalsFor;
        }
    }
}
