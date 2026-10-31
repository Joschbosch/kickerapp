package zur.koeln.kickertool.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.Pairing;
import zur.koeln.kickertool.domain.tournament.PlayerSlot;
import zur.koeln.kickertool.domain.tournament.StandInSuggester;
import zur.koeln.kickertool.domain.tournament.Team;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

class PlayerStatisticsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final StandInSuggester suggester = new NearestRankStandInSuggester();
    private final List<PlayerId> p = new ArrayList<>();

    PlayerStatisticsTest() {
        for (int i = 0; i < 8; i++) {
            p.add(PlayerId.random());
        }
    }

    /** Acht Spieler, eine Runde: Spiel 1 ist p0+p2 gegen p1+p3, Spiel 2 ist p4+p6 gegen p5+p7. */
    private Tournament running(String name, LocalDate date) {
        Tournament t = Tournament.plan(TournamentId.random(), name, date, TournamentConfig.defaults());
        for (int i = 0; i < p.size(); i++) {
            t.register(p.get(i), NOW.plusSeconds(i));
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

    private void decide(Tournament t, int position, int goalsA, int goalsB) {
        Match match = t.rounds().get(0).matches().get(position - 1);
        t.decideResult(match.id(), new MatchResult(goalsA, goalsB), suggester);
    }

    /** Frühjahrsturnier: p0+p2 gewinnen 10:4, das zweite Spiel endet 6:6. Beendet. */
    private Tournament finishedSpring() {
        Tournament t = running("Frühjahr", LocalDate.of(2026, 3, 1));
        decide(t, 1, 10, 4);
        decide(t, 2, 6, 6);
        t.finish();
        return t;
    }

    /** Herbstturnier: p1+p3 gewinnen 10:3, das zweite Spiel ist noch nicht gespielt. Läuft noch. */
    private Tournament runningAutumn() {
        Tournament t = running("Herbst", LocalDate.of(2026, 10, 5));
        decide(t, 1, 3, 10);
        return t;
    }

    @Test
    void sumsRunningAndFinishedTournamentsNewestFirst() {
        PlayerStatistics statistics = PlayerStatistics.of(p.get(0), List.of(finishedSpring(), runningAutumn()));

        assertThat(statistics.tournaments()).extracting(TournamentStanding::name).containsExactly("Herbst", "Frühjahr");
        assertThat(statistics.totals()).isEqualTo(new PlayerStatistics.Totals(
                2, 1, 1, 1, 2, 1, 0, 1, 13, 14, 3));
        assertThat(statistics.totals().goalDifference()).isEqualTo(-1);
    }

    @Test
    void reportsRankAndFiguresPerTournament() {
        PlayerStatistics statistics = PlayerStatistics.of(p.get(0), List.of(finishedSpring(), runningAutumn()));

        TournamentStanding autumn = statistics.tournaments().get(0);
        assertThat(autumn.tournamentStatus()).isEqualTo(TournamentStatus.RUNNING);
        assertThat(autumn.rank()).isEqualTo(7);
        assertThat(autumn.participantCount()).isEqualTo(8);
        assertThat(autumn.matchesPlayed()).isEqualTo(1);
        assertThat(autumn.losses()).isEqualTo(1);
        assertThat(autumn.goalsFor()).isEqualTo(3);
        assertThat(autumn.goalsAgainst()).isEqualTo(10);
        assertThat(autumn.goalDifference()).isEqualTo(-7);
        assertThat(autumn.points()).isZero();

        TournamentStanding spring = statistics.tournaments().get(1);
        assertThat(spring.tournamentStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(spring.rank()).isEqualTo(1);
        assertThat(spring.wins()).isEqualTo(1);
        assertThat(spring.points()).isEqualTo(3);
    }

    @Test
    void countsTitlesAndPodiumsOnlyInFinishedTournaments() {
        // p4 wurde im Frühjahr mit einem Unentschieden gemeinsam Dritter, im laufenden Herbst ist er Dritter
        PlayerStatistics statistics = PlayerStatistics.of(p.get(4), List.of(finishedSpring(), runningAutumn()));

        assertThat(statistics.tournaments()).extracting(TournamentStanding::rank).containsExactly(3, 3);
        assertThat(statistics.totals().finishedTournamentCount()).isEqualTo(1);
        assertThat(statistics.totals().titles()).isZero();
        assertThat(statistics.totals().podiums()).isEqualTo(1);
        assertThat(statistics.totals().draws()).isEqualTo(1);
        assertThat(statistics.totals().points()).isEqualTo(1);
    }

    @Test
    void ranksBeyondThirdAreNoPodium() {
        PlayerStatistics statistics = PlayerStatistics.of(p.get(1), List.of(finishedSpring()));

        assertThat(statistics.tournaments().get(0).rank()).isEqualTo(7);
        assertThat(statistics.totals().podiums()).isZero();
        assertThat(statistics.totals().titles()).isZero();
    }

    @Test
    void ignoresPlannedTournaments() {
        Tournament planned = Tournament.plan(TournamentId.random(), "Geplant", LocalDate.of(2026, 12, 1),
                TournamentConfig.defaults());
        planned.register(p.get(0), NOW);

        PlayerStatistics statistics = PlayerStatistics.of(p.get(0), List.of(planned, finishedSpring()));

        assertThat(statistics.tournaments()).extracting(TournamentStanding::name).containsExactly("Frühjahr");
        assertThat(statistics.totals().tournamentCount()).isEqualTo(1);
    }

    @Test
    void ignoresTournamentsTheCallerDidNotJoin() {
        PlayerStatistics statistics = PlayerStatistics.of(PlayerId.random(), List.of(finishedSpring()));

        assertThat(statistics.tournaments()).isEmpty();
        assertThat(statistics.totals()).isEqualTo(new PlayerStatistics.Totals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void aFinishedTournamentWithoutMatchesIsNoTitle() {
        Tournament t = Tournament.plan(TournamentId.random(), "Ausgefallen", LocalDate.of(2026, 5, 1),
                TournamentConfig.defaults());
        t.register(p.get(0), NOW);
        t.start();
        t.finish();

        PlayerStatistics statistics = PlayerStatistics.of(p.get(0), List.of(t));

        assertThat(statistics.tournaments().get(0).rank()).isEqualTo(1);
        assertThat(statistics.totals().finishedTournamentCount()).isEqualTo(1);
        assertThat(statistics.totals().titles()).isZero();
        assertThat(statistics.totals().podiums()).isZero();
    }

    @Test
    void withdrawnPlayersKeepTheirFigures() {
        Tournament t = runningAutumn();
        t.withdraw(p.get(1));

        PlayerStatistics statistics = PlayerStatistics.of(p.get(1), List.of(t));

        TournamentStanding standing = statistics.tournaments().get(0);
        assertThat(standing.participantStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
        assertThat(standing.wins()).isEqualTo(1);
        assertThat(standing.points()).isEqualTo(3);
        assertThat(statistics.totals().tournamentCount()).isEqualTo(1);
    }

    @Test
    void sortsTournamentsOfTheSameDayByName() {
        Tournament b = running("B-Turnier", LocalDate.of(2026, 6, 1));
        Tournament a = running("A-Turnier", LocalDate.of(2026, 6, 1));

        PlayerStatistics statistics = PlayerStatistics.of(p.get(0), List.of(b, a));

        assertThat(statistics.tournaments()).extracting(TournamentStanding::name)
                .containsExactly("A-Turnier", "B-Turnier");
    }
}
