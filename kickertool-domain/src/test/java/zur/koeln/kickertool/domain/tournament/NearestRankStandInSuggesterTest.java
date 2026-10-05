package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.player.PlayerId;

class NearestRankStandInSuggesterTest {

    private final NearestRankStandInSuggester suggester = new NearestRankStandInSuggester();

    private static RankingEntry entry(PlayerId id, int rank) {
        return new RankingEntry(id, ParticipantStatus.ACTIVE, rank, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void picksTheCandidateClosestToTheMatchPlayersAverageRank() {
        PlayerId a = PlayerId.random();
        PlayerId b = PlayerId.random();
        PlayerId near = PlayerId.random();
        PlayerId far = PlayerId.random();
        Ranking ranking = new Ranking(List.of(entry(a, 10), entry(b, 12), entry(far, 1), entry(near, 9)));
        Match match = Match.queued(MatchId.random(), 1, 1,
                new Team(new PlayerSlot(a), DummySlot.unassigned()),
                new Team(new PlayerSlot(b), new PlayerSlot(PlayerId.random())));

        assertThat(suggester.suggest(match, ranking, List.of(far, near))).contains(near);
    }

    @Test
    void returnsEmptyWithoutCandidates() {
        Match match = Match.queued(MatchId.random(), 1, 1,
                new Team(new PlayerSlot(PlayerId.random()), DummySlot.unassigned()),
                new Team(DummySlot.unassigned(), DummySlot.unassigned()));

        assertThat(suggester.suggest(match, new Ranking(List.of()), List.of())).isEmpty();
    }
}
