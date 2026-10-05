package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.player.PlayerId;

class RankingTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final StandInSuggester suggester = new NearestRankStandInSuggester();
    private final List<PlayerId> p = new ArrayList<>();

    private Tournament runningWithOneRoundOfEight(TournamentConfig config) {
        Tournament t = Tournament.plan(TournamentId.random(), "Ranking", LocalDate.of(2026, 10, 5), config);
        for (int i = 0; i < 8; i++) {
            PlayerId id = PlayerId.random();
            p.add(id);
            t.register(id, NOW.plusSeconds(i));
        }
        t.start();
        t.startNextRound(ctx -> {
            List<Pairing> pairings = new ArrayList<>();
            for (int i = 0; i < 8; i += 4) {
                pairings.add(new Pairing(
                        new Team(new PlayerSlot(ctx.players().get(i)), new PlayerSlot(ctx.players().get(i + 2))),
                        new Team(new PlayerSlot(ctx.players().get(i + 1)), new PlayerSlot(ctx.players().get(i + 3)))));
            }
            return pairings;
        }, suggester);
        return t;
    }

    private void play(Tournament t, int position, int goalsA, int goalsB) {
        Match m = t.rounds().get(0).matches().get(position - 1);
        t.decideResult(m.id(), new MatchResult(goalsA, goalsB), suggester);
    }

    @Test
    void awardsConfiguredPointsAndTracksStatistics() {
        Tournament t = runningWithOneRoundOfEight(new TournamentConfig(2, 5, 2, 1, 10, 5, null));
        Match m1 = t.rounds().get(0).matches().get(0);
        PlayerId winner = m1.teamA().realPlayers().get(0);
        PlayerId loser = m1.teamB().realPlayers().get(0);
        PlayerId drawer = t.rounds().get(0).matches().get(1).teamA().realPlayers().get(0);

        play(t, 1, 10, 4);
        play(t, 2, 6, 6);

        RankingEntry w = t.ranking().entryOf(winner).orElseThrow();
        assertThat(w.points()).isEqualTo(5);
        assertThat(w.wins()).isEqualTo(1);
        assertThat(w.goalsFor()).isEqualTo(10);
        assertThat(w.goalsAgainst()).isEqualTo(4);
        assertThat(w.goalDifference()).isEqualTo(6);

        RankingEntry l = t.ranking().entryOf(loser).orElseThrow();
        assertThat(l.points()).isEqualTo(1);
        assertThat(l.losses()).isEqualTo(1);
        assertThat(l.goalDifference()).isEqualTo(-6);

        RankingEntry d = t.ranking().entryOf(drawer).orElseThrow();
        assertThat(d.points()).isEqualTo(2);
        assertThat(d.draws()).isEqualTo(1);
    }

    @Test
    void sortsByPointsThenGoalDifferenceThenGoalsFor() {
        Tournament t = runningWithOneRoundOfEight(TournamentConfig.defaults());
        Match m1 = t.rounds().get(0).matches().get(0);
        Match m2 = t.rounds().get(0).matches().get(1);
        play(t, 1, 10, 9); // Sieger: 3 Punkte, +1
        play(t, 2, 10, 2); // Sieger: 3 Punkte, +8

        List<PlayerId> order = t.ranking().entries().stream().map(RankingEntry::playerId).toList();
        // Gewinner von Match 2 (+8) vor Gewinner von Match 1 (+1), dann Verlierer: 0 Punkte, -8 vor -1 → Match 1 Verlierer zuerst
        assertThat(order.subList(0, 2)).containsExactlyInAnyOrderElementsOf(m2.teamA().realPlayers());
        assertThat(order.subList(2, 4)).containsExactlyInAnyOrderElementsOf(m1.teamA().realPlayers());
        assertThat(order.subList(4, 6)).containsExactlyInAnyOrderElementsOf(m1.teamB().realPlayers());
        assertThat(order.subList(6, 8)).containsExactlyInAnyOrderElementsOf(m2.teamB().realPlayers());
    }

    @Test
    void playersWithIdenticalStandingShareTheRank() {
        Tournament t = runningWithOneRoundOfEight(TournamentConfig.defaults());
        // Vor jedem Ergebnis: alle 8 Spieler auf Rang 1
        assertThat(t.ranking().entries()).allMatch(e -> e.rank() == 1);

        play(t, 1, 10, 5);
        play(t, 2, 10, 5);

        List<Integer> ranks = t.ranking().entries().stream().map(RankingEntry::rank).toList();
        assertThat(ranks).containsExactly(1, 1, 1, 1, 5, 5, 5, 5);
    }

    @Test
    void unconfirmedResultsDoNotCount() {
        Tournament t = runningWithOneRoundOfEight(new TournamentConfig(2, 3, 1, 0, 10, 5, null));
        Match m1 = t.rounds().get(0).matches().get(0);
        t.submitResult(m1.id(), m1.teamA().realPlayers().get(0), new MatchResult(10, 2), suggester);

        assertThat(t.ranking().entries()).allMatch(e -> e.points() == 0 && e.matchesPlayed() == 0);
    }
}
