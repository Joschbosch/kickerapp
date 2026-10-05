package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.player.PlayerId;

class SwissDypTeamAssignmentStrategyTest {

    private static final int SEEDS = 200;

    private static List<PlayerId> players(int count) {
        return IntStream.range(0, count).mapToObj(i -> PlayerId.random()).toList();
    }

    /** Eine Rangliste, in der die Spieler in der gegebenen Reihenfolge die Ränge 1, 2, 3, ... haben. */
    private static Ranking distinctRanking(List<PlayerId> inRankOrder) {
        return new Ranking(IntStream.range(0, inRankOrder.size())
                .mapToObj(i -> entry(inRankOrder.get(i), i + 1)).toList());
    }

    private static Ranking sharedRanking(List<PlayerId> players, int rank) {
        return new Ranking(players.stream().map(p -> entry(p, rank)).toList());
    }

    private static RankingEntry entry(PlayerId id, int rank) {
        return new RankingEntry(id, ParticipantStatus.ACTIVE, rank, 0, 0, 0, 0, 0, 0, 0);
    }

    private static TournamentConfig config(int randomRounds) {
        return new TournamentConfig(4, 3, 1, 0, 10, 5, null, randomRounds);
    }

    private static AssignmentContext context(int round, List<PlayerId> players, Ranking ranking,
            List<Round> previous, int randomRounds) {
        return new AssignmentContext(round, players, ranking, previous, config(randomRounds));
    }

    private static Round roundOf(Team... teamsInPairs) {
        java.util.ArrayList<Match> matches = new java.util.ArrayList<>();
        for (int i = 0; i < teamsInPairs.length; i += 2) {
            matches.add(Match.queued(MatchId.random(), 1, i / 2 + 1, teamsInPairs[i], teamsInPairs[i + 1]));
        }
        return Round.restore(1, matches);
    }

    private static Team team(PlayerId a, PlayerId b) {
        return new Team(new PlayerSlot(a), new PlayerSlot(b));
    }

    private static Set<PlayerId> realPlayers(Pairing pairing) {
        Set<PlayerId> result = new HashSet<>(pairing.teamA().realPlayers());
        result.addAll(pairing.teamB().realPlayers());
        return result;
    }

    // ---------------------------------------------------------------- Vollständigkeit

    @Test
    void everyPlayerAppearsOnceAndDummiesFillTheGapInBothModes() {
        for (int randomRounds : new int[] {5, 0}) {
            for (int n = 1; n <= 60; n++) {
                List<PlayerId> players = players(n);
                List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(n)).assign(
                        context(2, players, distinctRanking(players), List.of(), randomRounds));

                Set<PlayerId> seen = new HashSet<>();
                int dummies = 0;
                for (Pairing pairing : pairings) {
                    for (Team team : List.of(pairing.teamA(), pairing.teamB())) {
                        dummies += team.dummyCount();
                        team.realPlayers().forEach(p -> assertThat(seen.add(p)).isTrue());
                    }
                }
                assertThat(seen).as("n=%d, randomRounds=%d", n, randomRounds).hasSize(n);
                assertThat(dummies).isEqualTo((4 - n % 4) % 4);
                assertThat(pairings).hasSize((n + 3) / 4);
            }
        }
    }

    @Test
    void fiftyPlayersNeedThirteenMatchesAndTwoDummies() {
        List<PlayerId> players = players(50);
        List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(1)).assign(
                context(3, players, distinctRanking(players), List.of(), 2));

        assertThat(pairings).hasSize(13);
        assertThat(pairings.stream().mapToInt(p -> p.teamA().dummyCount() + p.teamB().dummyCount()).sum())
                .isEqualTo(2);
    }

    @Test
    void dummiesNeverEndUpTogetherInOneTeamWhenTwoRealPlayersAreAvailable() {
        for (int n : new int[] {6, 10, 14}) {
            List<PlayerId> players = players(n);
            Pairing last = lastOf(new SwissDypTeamAssignmentStrategy(new Random(n)).assign(
                    context(3, players, distinctRanking(players), List.of(), 2)));

            assertThat(last.teamA().realPlayers()).hasSize(1);
            assertThat(last.teamB().realPlayers()).hasSize(1);
        }
    }

    private static Pairing lastOf(List<Pairing> pairings) {
        return pairings.get(pairings.size() - 1);
    }

    // ---------------------------------------------------------------- Zufallsrunden

    @Test
    void randomRoundsIgnoreTheRanking() {
        List<PlayerId> players = players(8);
        Ranking ranking = distinctRanking(players);
        boolean bestAndWorstMet = false;
        for (int seed = 0; seed < SEEDS && !bestAndWorstMet; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(2, players, ranking, List.of(), 2));
            bestAndWorstMet = pairings.stream()
                    .anyMatch(p -> realPlayers(p).containsAll(List.of(players.get(0), players.get(7))));
        }
        assertThat(bestAndWorstMet).as("Rang 1 und 8 spielen in einer Zufallsrunde irgendwann zusammen").isTrue();
    }

    @Test
    void randomRoundsEndAfterTheConfiguredNumber() {
        List<PlayerId> players = players(8);
        Ranking ranking = distinctRanking(players);
        for (int seed = 0; seed < SEEDS; seed++) {
            // Runde 3 bei 2 Zufallsrunden: nach Rang
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, players, ranking, List.of(), 2));
            assertThat(realPlayers(pairings.get(0))).containsExactlyInAnyOrderElementsOf(players.subList(0, 4));
        }
    }

    @Test
    void zeroRandomRoundsMeansRankBasedFromTheFirstRound() {
        List<PlayerId> players = players(8);
        Ranking ranking = distinctRanking(players);
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(1, players, ranking, List.of(), 0));
            assertThat(realPlayers(pairings.get(0))).containsExactlyInAnyOrderElementsOf(players.subList(0, 4));
        }
    }

    // ---------------------------------------------------------------- Rangblöcke

    @Test
    void rankBlocksOfFourPlayTogetherAndTheBestBlockComesFirst() {
        List<PlayerId> players = players(12);
        Ranking ranking = distinctRanking(players);
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, players, ranking, List.of(), 2));

            for (int block = 0; block < 3; block++) {
                assertThat(realPlayers(pairings.get(block)))
                        .containsExactlyInAnyOrderElementsOf(players.subList(block * 4, block * 4 + 4));
            }
        }
    }

    @Test
    void theIncompleteBlockWithDummiesIsTheLowestRanked() {
        List<PlayerId> players = players(10);
        Ranking ranking = distinctRanking(players);
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, players, ranking, List.of(), 2));

            assertThat(realPlayers(lastOf(pairings))).containsExactlyInAnyOrder(players.get(8), players.get(9));
        }
    }

    @Test
    void playersWithTheSameRankAreShuffledBeforeBuildingBlocks() {
        List<PlayerId> players = players(8);
        Ranking allEqual = sharedRanking(players, 1);
        Set<Set<PlayerId>> firstBlocks = new HashSet<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, players, allEqual, List.of(), 2));
            firstBlocks.add(realPlayers(pairings.get(0)));
        }
        assertThat(firstBlocks).as("nicht immer die zuerst Angemeldeten im ersten Block").hasSizeGreaterThan(5);
    }

    @Test
    void ranksTiedInsideAGroupOnlyMixWithinTheirTieAndNeighbours() {
        // Rang 1: a, b | Rang 3: c, d | Rang 5: e, f, g, h. Block 1 = a, b, c, d, immer.
        List<PlayerId> p = players(8);
        Ranking ranking = new Ranking(List.of(entry(p.get(0), 1), entry(p.get(1), 1), entry(p.get(2), 3),
                entry(p.get(3), 3), entry(p.get(4), 5), entry(p.get(5), 5), entry(p.get(6), 5), entry(p.get(7), 5)));
        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, p, ranking, List.of(), 2));
            assertThat(realPlayers(pairings.get(0))).containsExactlyInAnyOrderElementsOf(p.subList(0, 4));
        }
    }

    // ---------------------------------------------------------------- Partnerregel

    @Test
    void lastRoundsPartnersAreSeparatedInsideTheBlock() {
        List<PlayerId> p = players(4);
        Round previous = roundOf(team(p.get(0), p.get(1)), team(p.get(2), p.get(3)));

        for (int randomRounds : new int[] {5, 0}) {
            for (int seed = 0; seed < SEEDS; seed++) {
                Pairing pairing = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                        context(2, p, distinctRanking(p), List.of(previous), randomRounds)).get(0);

                assertThat(List.of(Set.copyOf(pairing.teamA().realPlayers()), Set.copyOf(pairing.teamB().realPlayers())))
                        .as("Zufall=%d, Seed=%d", randomRounds, seed)
                        .doesNotContain(Set.of(p.get(0), p.get(1)), Set.of(p.get(2), p.get(3)));
            }
        }
    }

    @Test
    void partnerRuleAlsoHoldsInTheBlockWithADummy() {
        List<PlayerId> p = players(4);
        Round previous = roundOf(team(p.get(0), p.get(1)), team(p.get(2), p.get(3)));
        List<PlayerId> threeLeft = p.subList(0, 3);

        for (int seed = 0; seed < SEEDS; seed++) {
            Pairing pairing = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(2, threeLeft, distinctRanking(threeLeft), List.of(previous), 0)).get(0);

            for (Team team : List.of(pairing.teamA(), pairing.teamB())) {
                assertThat(Set.copyOf(team.realPlayers())).isNotEqualTo(Set.of(p.get(0), p.get(1)));
            }
        }
    }

    @Test
    void onlyTheLastRoundCounts() {
        List<PlayerId> p = players(4);
        Round older = roundOf(team(p.get(0), p.get(1)), team(p.get(2), p.get(3)));
        Round last = roundOf(team(p.get(0), p.get(2)), team(p.get(1), p.get(3)));
        Set<Set<PlayerId>> seenTeams = new HashSet<>();

        for (int seed = 0; seed < SEEDS; seed++) {
            Pairing pairing = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(3, p, distinctRanking(p), List.of(older, last), 0)).get(0);
            seenTeams.add(Set.copyOf(pairing.teamA().realPlayers()));
            seenTeams.add(Set.copyOf(pairing.teamB().realPlayers()));
        }

        assertThat(seenTeams).as("Partner von vorletzter Runde sind wieder erlaubt")
                .contains(Set.of(p.get(0), p.get(1)), Set.of(p.get(2), p.get(3)));
        assertThat(seenTeams).doesNotContain(Set.of(p.get(0), p.get(2)), Set.of(p.get(1), p.get(3)));
    }

    // ---------------------------------------------------------------- im Turnier

    @Test
    void noPartnersRepeatBetweenConsecutiveRoundsInAFiftyPlayerTournament() {
        StandInSuggester suggester = new NearestRankStandInSuggester();
        TeamAssignmentStrategy strategy = new SwissDypTeamAssignmentStrategy(new Random(42));
        Tournament tournament = Tournament.plan(TournamentId.random(), "Groß", LocalDate.of(2026, 10, 5),
                new TournamentConfig(6, 3, 1, 0, 10, 5, null, 2));
        for (int i = 0; i < 50; i++) {
            tournament.register(PlayerId.random(), Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i));
        }
        tournament.start();

        Random scores = new Random(7);
        Set<Set<PlayerId>> previousPartners = Set.of();
        for (int round = 1; round <= 8; round++) {
            tournament.startNextRound(strategy, suggester);
            Round current = tournament.currentRound().orElseThrow();
            assertThat(current.matches()).hasSize(13);

            Set<Set<PlayerId>> partners = new HashSet<>();
            for (Match match : current.matches()) {
                for (Team team : List.of(match.teamA(), match.teamB())) {
                    if (team.realPlayers().size() == 2) {
                        partners.add(Set.copyOf(team.realPlayers()));
                    }
                }
            }
            assertThat(java.util.Collections.disjoint(partners, previousPartners))
                    .as("Runde %d wiederholt Partner der Vorrunde", round).isTrue();
            previousPartners = partners;

            for (int guard = 0; !current.isComplete() && guard < 20; guard++) {
                for (Match match : current.matches()) {
                    if (match.status() == MatchStatus.ON_TABLE) {
                        tournament.decideResult(match.id(), new MatchResult(10, scores.nextInt(10)), suggester);
                    }
                }
            }
            assertThat(current.isComplete()).isTrue();
        }
    }

    // ---------------------------------------------------------------- Dummy-Rotation

    private static Team withDummy(PlayerId player) {
        return new Team(new PlayerSlot(player), DummySlot.unassigned());
    }

    /** Vorrunde mit 10 Spielern: Spieler 8 und 9 (die letzten beiden) standen im Match mit Dummys. */
    private static Round previousRoundWithDummiesForLastTwo(List<PlayerId> p) {
        return roundOf(team(p.get(0), p.get(1)), team(p.get(2), p.get(3)),
                team(p.get(4), p.get(5)), team(p.get(6), p.get(7)),
                withDummy(p.get(8)), withDummy(p.get(9)));
    }

    @Test
    void playersWhoAlreadyMetDummiesAreSparedUntilOthersHadTheirTurn() {
        List<PlayerId> p = players(10);
        Ranking ranking = distinctRanking(p);
        Round previous = previousRoundWithDummiesForLastTwo(p);

        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(2, p, ranking, List.of(previous), 0));

            assertThat(realPlayers(lastOf(pairings)))
                    .as("Seed %d: niedrigste Ränge unter denen, die noch nie gegen Dummys spielten", seed)
                    .containsExactlyInAnyOrder(p.get(6), p.get(7));
        }
    }

    @Test
    void rotationAlsoAppliesInRandomRounds() {
        List<PlayerId> p = players(10);
        Round previous = previousRoundWithDummiesForLastTwo(p);
        Set<PlayerId> everChosen = new HashSet<>();

        for (int seed = 0; seed < SEEDS; seed++) {
            List<Pairing> pairings = new SwissDypTeamAssignmentStrategy(new Random(seed)).assign(
                    context(2, p, distinctRanking(p), List.of(previous), 5));
            Set<PlayerId> chosen = realPlayers(lastOf(pairings));
            assertThat(chosen).doesNotContain(p.get(8), p.get(9));
            everChosen.addAll(chosen);
        }
        assertThat(everChosen).as("zufällig unter allen anderen").hasSize(8);
    }

    @Test
    void everyoneGetsDummiesAboutEquallyOften() {
        for (int randomRounds : new int[] {0, 100}) {
            Tournament tournament = playRounds(10, 3, randomRounds, 10, 3);

            assertThat(dummyMatchSpread(tournament))
                    .as("Spreizung der Dummy-Matches bei randomRounds=%d", randomRounds)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void inAFiftyPlayerTournamentNobodyMeetsDummiesTwiceWithinEightRounds() {
        Tournament tournament = playRounds(50, 6, 2, 8, 9);

        // 8 Runden mit je 2 betroffenen Spielern: 16 verschiedene Spieler
        assertThat(dummyMatchCounts(tournament).values()).allMatch(count -> count <= 1);
        assertThat(dummyMatchCounts(tournament).values().stream().filter(count -> count == 1).count()).isEqualTo(16);
    }

    private Tournament playRounds(int playerCount, int tables, int randomRounds, int rounds, long seed) {
        StandInSuggester suggester = new NearestRankStandInSuggester();
        TeamAssignmentStrategy strategy = new SwissDypTeamAssignmentStrategy(new Random(seed));
        Tournament tournament = Tournament.plan(TournamentId.random(), "Rotation", LocalDate.of(2026, 10, 5),
                new TournamentConfig(tables, 3, 1, 0, 10, 5, null, randomRounds));
        for (int i = 0; i < playerCount; i++) {
            tournament.register(PlayerId.random(), Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i));
        }
        tournament.start();
        Random scores = new Random(seed);
        for (int round = 1; round <= rounds; round++) {
            tournament.startNextRound(strategy, suggester);
            Round current = tournament.currentRound().orElseThrow();
            for (int guard = 0; !current.isComplete() && guard < 30; guard++) {
                for (Match match : current.matches()) {
                    if (match.status() == MatchStatus.ON_TABLE) {
                        tournament.decideResult(match.id(), new MatchResult(10, scores.nextInt(10)), suggester);
                    }
                }
            }
            assertThat(current.isComplete()).isTrue();
        }
        return tournament;
    }

    /** Pro Spieler: in wie vielen Runden stand er in einem Match mit Dummys. */
    private static java.util.Map<PlayerId, Integer> dummyMatchCounts(Tournament tournament) {
        java.util.Map<PlayerId, Integer> counts = new java.util.HashMap<>();
        tournament.participants().forEach(participant -> counts.put(participant.playerId(), 0));
        for (Round round : tournament.rounds()) {
            for (Match match : round.matches()) {
                if (match.teamA().dummyCount() + match.teamB().dummyCount() > 0) {
                    match.realPlayers().forEach(player -> counts.merge(player, 1, Integer::sum));
                }
            }
        }
        return counts;
    }

    private static int dummyMatchSpread(Tournament tournament) {
        var values = dummyMatchCounts(tournament).values();
        return java.util.Collections.max(values) - java.util.Collections.min(values);
    }
}
