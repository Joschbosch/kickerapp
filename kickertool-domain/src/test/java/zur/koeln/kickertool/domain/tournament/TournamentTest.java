package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.NotPermittedException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.PlayerId;

class TournamentTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final StandInSuggester suggester = new NearestRankStandInSuggester();
    private final TeamAssignmentStrategy inOrder = new InOrderStrategy();
    private final List<PlayerId> players = new ArrayList<>();

    /** Gruppen aus je 4 Spielern in Anmeldereihenfolge: (1,3) gegen (2,4), usw. */
    private static final class InOrderStrategy implements TeamAssignmentStrategy {
        @Override
        public List<Pairing> assign(AssignmentContext context) {
            List<Pairing> pairings = new ArrayList<>();
            List<PlayerId> ps = context.players();
            for (int i = 0; i < ps.size(); i += 4) {
                Slot[] s = new Slot[4];
                for (int k = 0; k < 4; k++) {
                    s[k] = i + k < ps.size() ? new PlayerSlot(ps.get(i + k)) : DummySlot.unassigned();
                }
                pairings.add(new Pairing(new Team(s[0], s[2]), new Team(s[1], s[3])));
            }
            return pairings;
        }
    }

    private Tournament planned(int playerCount, int tables) {
        TournamentConfig config = new TournamentConfig(tables, 3, 1, 0, 10, 5, null);
        Tournament tournament = Tournament.plan(TournamentId.random(), "Testturnier", LocalDate.of(2026, 10, 5), config);
        for (int i = 0; i < playerCount; i++) {
            PlayerId id = PlayerId.random();
            players.add(id);
            tournament.register(id, NOW.plusSeconds(i));
        }
        return tournament;
    }

    private Tournament runningWithRound(int playerCount, int tables) {
        Tournament tournament = planned(playerCount, tables);
        tournament.start();
        tournament.startNextRound(inOrder, suggester);
        return tournament;
    }

    private static Match match(Tournament t, int round, int position) {
        return t.rounds().get(round - 1).matches().get(position - 1);
    }

    private PlayerId player(int oneBased) {
        return players.get(oneBased - 1);
    }

    /** Team A gewinnt jedes Match, Team B bestätigt. Spielt Wellen nacheinander durch. */
    private void playOutRound(Tournament t, MatchResult result) {
        Round round = t.currentRound().orElseThrow();
        for (int guard = 0; !round.isComplete() && guard < 100; guard++) {
            for (Match m : round.matches()) {
                if (m.status() == MatchStatus.ON_TABLE) {
                    t.submitResult(m.id(), m.teamA().actingPlayers().get(0), result, suggester);
                    t.confirmResult(m.id(), m.teamB().actingPlayers().get(0));
                }
            }
        }
        assertThat(round.isComplete()).isTrue();
    }

    // ---------------------------------------------------------------- Anmeldung und Start

    @Test
    void tournamentCannotStartWithoutPlayers() {
        Tournament t = planned(0, 2);
        assertThatThrownBy(t::start).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void registeringTwiceIsRejected() {
        Tournament t = planned(2, 1);
        assertThatThrownBy(() -> t.register(player(1), NOW)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void unregisteringIsOnlyPossibleBeforeStart() {
        Tournament t = planned(4, 1);
        t.unregister(player(4));
        assertThat(t.participants()).hasSize(3);

        t.start();
        assertThatThrownBy(() -> t.unregister(player(1))).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void roundCannotStartBeforeTournamentStart() {
        Tournament t = planned(4, 1);
        assertThatThrownBy(() -> t.startNextRound(inOrder, suggester)).isInstanceOf(RuleViolationException.class);
    }

    // ---------------------------------------------------------------- Tische und Wellen

    @Test
    void firstWaveOccupiesOnlyAsManyTablesAsConfigured() {
        Tournament t = runningWithRound(12, 2);

        assertThat(match(t, 1, 1).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(match(t, 1, 1).tableNumber()).contains(1);
        assertThat(match(t, 1, 2).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(match(t, 1, 2).tableNumber()).contains(2);
        assertThat(match(t, 1, 3).status()).isEqualTo(MatchStatus.QUEUED);
        assertThat(match(t, 1, 3).tableNumber()).isEmpty();
    }

    @Test
    void nextWaveStartsOnlyWhenAllMatchesOfTheWaveHaveAResult() {
        Tournament t = runningWithRound(12, 2);

        Match first = match(t, 1, 1);
        t.submitResult(first.id(), player(1), new MatchResult(10, 4), suggester);
        assertThat(match(t, 1, 3).status()).as("zweites Match der Welle läuft noch").isEqualTo(MatchStatus.QUEUED);

        Match second = match(t, 1, 2);
        t.submitResult(second.id(), player(5), new MatchResult(7, 10), suggester);
        assertThat(match(t, 1, 3).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(match(t, 1, 3).tableNumber()).contains(1);
    }

    @Test
    void waveAdvancesWithoutWaitingForConfirmation() {
        Tournament t = runningWithRound(8, 1);

        t.submitResult(match(t, 1, 1).id(), player(1), new MatchResult(10, 0), suggester);

        assertThat(match(t, 1, 1).status()).isEqualTo(MatchStatus.RESULT_ENTERED);
        assertThat(match(t, 1, 2).status()).isEqualTo(MatchStatus.ON_TABLE);
    }

    @Test
    void tableCountChangeAppliesFromTheNextWave() {
        Tournament t = runningWithRound(16, 1);
        t.updateConfig(new TournamentConfig(3, 3, 1, 0, 10, 5, null));

        assertThat(match(t, 1, 2).status()).as("laufende Welle bleibt unverändert").isEqualTo(MatchStatus.QUEUED);

        t.submitResult(match(t, 1, 1).id(), player(1), new MatchResult(10, 1), suggester);
        assertThat(match(t, 1, 2).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(match(t, 1, 3).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(match(t, 1, 4).status()).isEqualTo(MatchStatus.ON_TABLE);
    }

    @Test
    void roundCannotStartUntilAllResultsAreConfirmed() {
        Tournament t = runningWithRound(8, 2);
        t.submitResult(match(t, 1, 1).id(), player(1), new MatchResult(10, 2), suggester);
        t.confirmResult(match(t, 1, 1).id(), player(2));
        t.submitResult(match(t, 1, 2).id(), player(5), new MatchResult(10, 2), suggester);

        assertThatThrownBy(() -> t.startNextRound(inOrder, suggester))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("nicht abgeschlossen");

        t.confirmResult(match(t, 1, 2).id(), player(6));
        t.startNextRound(inOrder, suggester);
        assertThat(t.rounds()).hasSize(2);
    }

    // ---------------------------------------------------------------- Dummys

    @Test
    void missingPlayersAreFilledWithDummiesAndStandInsNotAtATable() {
        Tournament t = runningWithRound(10, 1);
        Match dummyMatch = match(t, 1, 3);
        assertThat(dummyMatch.teamA().dummyCount() + dummyMatch.teamB().dummyCount()).isEqualTo(2);
        assertThat(dummyMatch.status()).isEqualTo(MatchStatus.QUEUED);

        t.submitResult(match(t, 1, 1).id(), player(1), new MatchResult(10, 3), suggester);
        t.submitResult(match(t, 1, 2).id(), player(5), new MatchResult(10, 3), suggester);

        Match onTable = match(t, 1, 3);
        assertThat(onTable.status()).isEqualTo(MatchStatus.ON_TABLE);
        Set<PlayerId> standIns = new java.util.HashSet<>();
        for (Team team : List.of(onTable.teamA(), onTable.teamB())) {
            team.slots().stream().filter(DummySlot.class::isInstance).map(DummySlot.class::cast)
                    .forEach(d -> standIns.add(d.standInPlayer().orElseThrow()));
        }
        assertThat(standIns).as("zwei verschiedene Einspringer").hasSize(2);
        assertThat(standIns).doesNotContain(player(9), player(10));
    }

    @Test
    void standInsGetNoPointsForTheDummyMatch() {
        Tournament t = runningWithRound(10, 1);
        playOutRound(t, new MatchResult(10, 0));

        Ranking ranking = t.ranking();
        for (PlayerId p : players) {
            assertThat(ranking.entryOf(p).orElseThrow().matchesPlayed())
                    .as("jeder Spieler hat genau ein Match gewertet bekommen").isEqualTo(1);
        }
    }

    @Test
    void standInCanEnterAndConfirmResults() {
        Tournament t = runningWithRound(9, 1);
        // Spieler 9 + 3 Dummys: Team B besteht nur aus Einspringern
        t.submitResult(match(t, 1, 1).id(), player(1), new MatchResult(10, 0), suggester);
        t.submitResult(match(t, 1, 2).id(), player(5), new MatchResult(10, 0), suggester);
        Match m = match(t, 1, 3);
        PlayerId standInOfB = m.teamB().actingPlayers().get(0);

        t.submitResult(m.id(), player(9), new MatchResult(3, 7), suggester);
        t.confirmResult(m.id(), standInOfB);

        assertThat(m.status()).isEqualTo(MatchStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------- Ergebnisse

    @Test
    void onlyTheOpposingTeamCanConfirmOrReject() {
        Tournament t = runningWithRound(8, 2);
        MatchId id = match(t, 1, 1).id();
        t.submitResult(id, player(1), new MatchResult(10, 6), suggester);

        assertThatThrownBy(() -> t.confirmResult(id, player(3)))
                .as("Partner des Eintragenden").isInstanceOf(NotPermittedException.class);
        assertThatThrownBy(() -> t.confirmResult(id, player(1)))
                .as("Eintragender selbst").isInstanceOf(NotPermittedException.class);
        assertThatThrownBy(() -> t.confirmResult(id, player(7)))
                .as("Spieler eines anderen Matches").isInstanceOf(NotPermittedException.class);
        assertThatThrownBy(() -> t.rejectResult(id, player(3))).isInstanceOf(NotPermittedException.class);

        t.confirmResult(id, player(4));
        assertThat(match(t, 1, 1).status()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(match(t, 1, 1).resultSource()).contains(ResultSource.TEAM);
    }

    @Test
    void playersOfOtherMatchesCannotEnterResults() {
        Tournament t = runningWithRound(8, 2);
        assertThatThrownBy(() -> t.submitResult(match(t, 1, 1).id(), player(5), new MatchResult(10, 1), suggester))
                .isInstanceOf(NotPermittedException.class);
    }

    @Test
    void resultCannotBeEnteredForQueuedMatch() {
        Tournament t = runningWithRound(8, 1);
        assertThatThrownBy(() -> t.submitResult(match(t, 1, 2).id(), player(5), new MatchResult(10, 1), suggester))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void rejectedResultIsDecidedByAdmin() {
        Tournament t = runningWithRound(8, 2);
        MatchId id = match(t, 1, 1).id();
        t.submitResult(id, player(1), new MatchResult(10, 6), suggester);
        t.rejectResult(id, player(2));

        assertThat(match(t, 1, 1).status()).isEqualTo(MatchStatus.DISPUTED);
        assertThat(t.ranking().entryOf(player(1)).orElseThrow().matchesPlayed()).isZero();

        t.decideResult(id, new MatchResult(8, 10), suggester);
        assertThat(match(t, 1, 1).status()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(match(t, 1, 1).resultSource()).contains(ResultSource.ADMIN);
        assertThat(t.ranking().entryOf(player(2)).orElseThrow().points()).isEqualTo(3);
    }

    @Test
    void adminCanCorrectConfirmedResultAndRankingFollows() {
        Tournament t = runningWithRound(8, 2);
        MatchId id = match(t, 1, 1).id();
        t.submitResult(id, player(1), new MatchResult(10, 6), suggester);
        t.confirmResult(id, player(2));
        assertThat(t.ranking().entryOf(player(1)).orElseThrow().points()).isEqualTo(3);

        t.decideResult(id, new MatchResult(5, 5), suggester);

        assertThat(t.ranking().entryOf(player(1)).orElseThrow().points()).isEqualTo(1);
        assertThat(t.ranking().entryOf(player(2)).orElseThrow().points()).isEqualTo(1);
    }

    @Test
    void adminCannotSetResultForQueuedMatch() {
        Tournament t = runningWithRound(8, 1);
        assertThatThrownBy(() -> t.decideResult(match(t, 1, 2).id(), new MatchResult(10, 0), suggester))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void resultsAreValidatedAgainstTheGoalLimit() {
        Tournament t = runningWithRound(8, 2);
        MatchId id = match(t, 1, 1).id();

        assertThatThrownBy(() -> t.submitResult(id, player(1), new MatchResult(11, 3), suggester))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.submitResult(id, player(1), new MatchResult(10, 10), suggester))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.decideResult(id, new MatchResult(12, 3), suggester))
                .isInstanceOf(IllegalArgumentException.class);

        t.submitResult(id, player(1), new MatchResult(7, 7), suggester);
        assertThat(match(t, 1, 1).status()).as("Unentschieden nach Zeitablauf ist erlaubt")
                .isEqualTo(MatchStatus.RESULT_ENTERED);
    }

    // ---------------------------------------------------------------- Pause, Ausscheiden, Nachmelden

    @Test
    void pausedPlayerIsNotDrawnInTheNextRoundButCanReturn() {
        Tournament t = runningWithRound(8, 2);
        t.pause(player(8));
        assertThat(match(t, 1, 2).realPlayers()).as("aktuelle Runde unverändert").contains(player(8));
        playOutRound(t, new MatchResult(10, 5));

        t.startNextRound(inOrder, suggester);
        Set<PlayerId> inRound2 = t.rounds().get(1).matches().stream()
                .flatMap(m -> m.realPlayers().stream()).collect(Collectors.toSet());
        assertThat(inRound2).hasSize(7).doesNotContain(player(8));
        assertThat(t.rounds().get(1).matches()).hasSize(2);

        playOutRound(t, new MatchResult(10, 5));
        t.resume(player(8));
        t.startNextRound(inOrder, suggester);
        assertThat(t.rounds().get(2).matches().stream().flatMap(m -> m.realPlayers().stream()))
                .contains(player(8));
    }

    @Test
    void withdrawnPlayerKeepsPointsIsMarkedAndNeverReturns() {
        Tournament t = runningWithRound(8, 2);
        playOutRound(t, new MatchResult(10, 5));
        PlayerId winner = match(t, 1, 1).teamA().realPlayers().get(0);
        t.withdraw(winner);

        RankingEntry entry = t.ranking().entryOf(winner).orElseThrow();
        assertThat(entry.status()).isEqualTo(ParticipantStatus.WITHDRAWN);
        assertThat(entry.points()).isEqualTo(3);

        t.startNextRound(inOrder, suggester);
        assertThat(t.rounds().get(1).matches().stream().flatMap(m -> m.realPlayers().stream()))
                .doesNotContain(winner);
        assertThatThrownBy(() -> t.resume(winner)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> t.pause(winner)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void latePlayerJoinsFromTheNextRound() {
        Tournament t = runningWithRound(8, 2);
        PlayerId late = PlayerId.random();
        t.register(late, NOW.plusSeconds(1000));
        assertThat(t.rounds().get(0).matches().stream().flatMap(m -> m.realPlayers().stream())).doesNotContain(late);

        playOutRound(t, new MatchResult(10, 5));
        t.startNextRound(inOrder, suggester);

        assertThat(t.rounds().get(1).matches().stream().flatMap(m -> m.realPlayers().stream())).contains(late);
    }

    @Test
    void finishingRequiresACompleteRound() {
        Tournament t = runningWithRound(8, 2);
        assertThatThrownBy(t::finish).isInstanceOf(RuleViolationException.class);

        playOutRound(t, new MatchResult(10, 5));
        t.finish();
        assertThat(t.status()).isEqualTo(TournamentStatus.FINISHED);
        assertThatThrownBy(() -> t.register(PlayerId.random(), NOW)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> t.startNextRound(inOrder, suggester)).isInstanceOf(RuleViolationException.class);
    }

    // ---------------------------------------------------------------- Auslosung validieren

    @Test
    void invalidAssignmentIsRejected() {
        Tournament t = planned(8, 2);
        t.start();
        TeamAssignmentStrategy forgetsPlayers = ctx -> List.of(new Pairing(
                new Team(new PlayerSlot(player(1)), new PlayerSlot(player(2))),
                new Team(new PlayerSlot(player(3)), new PlayerSlot(player(4)))));
        assertThatThrownBy(() -> t.startNextRound(forgetsPlayers, suggester))
                .isInstanceOf(RuleViolationException.class);

        TeamAssignmentStrategy duplicates = ctx -> List.of(
                new Pairing(new Team(new PlayerSlot(player(1)), new PlayerSlot(player(2))),
                        new Team(new PlayerSlot(player(3)), new PlayerSlot(player(4)))),
                new Pairing(new Team(new PlayerSlot(player(5)), new PlayerSlot(player(6))),
                        new Team(new PlayerSlot(player(7)), new PlayerSlot(player(1)))));
        assertThatThrownBy(() -> t.startNextRound(duplicates, suggester)).isInstanceOf(RuleViolationException.class);
        assertThat(t.rounds()).isEmpty();
    }

    @Test
    void tooManyDummiesAreRejected() {
        Tournament t = planned(4, 1);
        t.start();
        TeamAssignmentStrategy fourDummies = ctx -> List.of(
                new Pairing(new Team(new PlayerSlot(player(1)), DummySlot.unassigned()),
                        new Team(DummySlot.unassigned(), DummySlot.unassigned())),
                new Pairing(new Team(new PlayerSlot(player(2)), DummySlot.unassigned()),
                        new Team(new PlayerSlot(player(3)), new PlayerSlot(player(4)))));
        assertThatThrownBy(() -> t.startNextRound(fourDummies, suggester)).isInstanceOf(RuleViolationException.class);
    }
}
