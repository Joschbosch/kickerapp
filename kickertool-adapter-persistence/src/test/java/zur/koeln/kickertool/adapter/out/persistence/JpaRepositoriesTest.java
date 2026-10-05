package zur.koeln.kickertool.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import zur.koeln.kickertool.application.port.out.PlayerAlreadyExistsException;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.DummySlot;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.MatchStatus;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.ParticipantStatus;
import zur.koeln.kickertool.domain.tournament.ResultSource;
import zur.koeln.kickertool.domain.tournament.SwissDypTeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

@DataJpaTest
@Import(PersistenceConfiguration.class)
class JpaRepositoriesTest {

    @Autowired
    private TournamentRepository tournaments;

    @Autowired
    private PlayerRepository players;

    @Autowired
    private TestEntityManager entityManager;

    private final List<Player> created = new ArrayList<>();
    private final NearestRankStandInSuggester suggester = new NearestRankStandInSuggester();
    private final SwissDypTeamAssignmentStrategy strategy = new SwissDypTeamAssignmentStrategy(new Random(1));

    private Player newPlayer(String name) {
        Player player = players.save(new Player(PlayerId.random(), "sub-" + name, name));
        created.add(player);
        return player;
    }

    private Tournament plannedWithPlayers(int count, int tables) {
        Tournament tournament = Tournament.plan(TournamentId.random(), "Herbstturnier", LocalDate.of(2026, 10, 5),
                new TournamentConfig(tables, 3, 1, 0, 10, 5, 8, 3));
        for (int i = 1; i <= count; i++) {
            tournament.register(newPlayer("Spieler" + i).id(), Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i));
        }
        return tournament;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---------------------------------------------------------------- Turnier

    @Test
    void roundTripsPlannedTournamentWithConfigAndParticipants() {
        Tournament tournament = plannedWithPlayers(3, 4);
        tournaments.save(tournament);
        flushAndClear();

        Tournament loaded = tournaments.findById(tournament.id()).orElseThrow();

        assertThat(loaded.name()).isEqualTo("Herbstturnier");
        assertThat(loaded.date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(loaded.status()).isEqualTo(TournamentStatus.PLANNED);
        assertThat(loaded.config()).isEqualTo(new TournamentConfig(4, 3, 1, 0, 10, 5, 8, 3));
        assertThat(loaded.participants()).hasSize(3)
                .allSatisfy(p -> assertThat(p.status()).isEqualTo(ParticipantStatus.ACTIVE));
        assertThat(loaded.participants().get(0).registeredAt()).isEqualTo(Instant.parse("2026-10-01T10:00:01Z"));
        assertThat(loaded.rounds()).isEmpty();
    }

    @Test
    void roundTripsRoundsMatchesDummiesStandInsAndResults() {
        Tournament tournament = plannedWithPlayers(10, 2);
        tournament.start();
        tournament.startNextRound(strategy, suggester);
        Match first = tournament.rounds().get(0).matches().get(0);
        tournament.submitResult(first.id(), first.teamA().actingPlayers().get(0), new MatchResult(10, 4), suggester);
        tournament.confirmResult(first.id(), first.teamB().actingPlayers().get(0));
        Match second = tournament.rounds().get(0).matches().get(1);
        tournament.submitResult(second.id(), second.teamA().actingPlayers().get(0), new MatchResult(3, 7), suggester);
        // Welle 2: das Dummy-Match steht nun am Tisch und hat Einspringer
        tournaments.save(tournament);
        flushAndClear();

        Tournament loaded = tournaments.findById(tournament.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(TournamentStatus.RUNNING);
        assertThat(loaded.rounds()).hasSize(1);
        List<Match> expected = tournament.rounds().get(0).matches();
        List<Match> actual = loaded.rounds().get(0).matches();
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            Match e = expected.get(i);
            Match a = actual.get(i);
            assertThat(a.id()).isEqualTo(e.id());
            assertThat(a.position()).isEqualTo(e.position());
            assertThat(a.status()).isEqualTo(e.status());
            assertThat(a.tableNumber()).isEqualTo(e.tableNumber());
            assertThat(a.teamA()).isEqualTo(e.teamA());
            assertThat(a.teamB()).isEqualTo(e.teamB());
            assertThat(a.result()).isEqualTo(e.result());
            assertThat(a.resultEnteredBy()).isEqualTo(e.resultEnteredBy());
            assertThat(a.resultEnteredSide()).isEqualTo(e.resultEnteredSide());
            assertThat(a.resultSource()).isEqualTo(e.resultSource());
        }
        assertThat(actual.get(0).resultSource()).contains(ResultSource.TEAM);
        assertThat(actual.get(1).status()).isEqualTo(MatchStatus.RESULT_ENTERED);
        Match dummyMatch = actual.get(2);
        assertThat(dummyMatch.status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(dummyMatch.teamA().dummyCount() + dummyMatch.teamB().dummyCount()).isEqualTo(2);
        assertThat(dummyMatch.teamA().slots().stream().filter(DummySlot.class::isInstance).map(DummySlot.class::cast))
                .allSatisfy(d -> assertThat(d.standInPlayer()).isPresent());
        assertThat(loaded.ranking()).isEqualTo(tournament.ranking());
    }

    @Test
    void savingAgainUpdatesInsteadOfDuplicating() {
        Tournament tournament = plannedWithPlayers(8, 2);
        tournament.start();
        tournament.startNextRound(strategy, suggester);
        tournaments.save(tournament);
        flushAndClear();

        Tournament loaded = tournaments.findByIdForUpdate(tournament.id()).orElseThrow();
        PlayerId paused = loaded.participants().get(0).playerId();
        loaded.pause(paused);
        Match match = loaded.rounds().get(0).matches().get(0);
        loaded.decideResult(match.id(), new MatchResult(10, 2), suggester);
        loaded.updateConfig(new TournamentConfig(3, 5, 2, 1, 7, 4, null, 0));
        tournaments.save(loaded);
        flushAndClear();

        Tournament reloaded = tournaments.findById(tournament.id()).orElseThrow();
        assertThat(reloaded.participants()).hasSize(8);
        assertThat(reloaded.findParticipant(paused).orElseThrow().status()).isEqualTo(ParticipantStatus.PAUSED);
        assertThat(reloaded.rounds().get(0).matches()).hasSize(2);
        assertThat(reloaded.rounds().get(0).matches().get(0).status()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(reloaded.rounds().get(0).matches().get(0).resultSource()).contains(ResultSource.ADMIN);
        assertThat(reloaded.config()).isEqualTo(new TournamentConfig(3, 5, 2, 1, 7, 4, null, 0));
    }

    @Test
    void removedParticipantsAreDeleted() {
        Tournament tournament = plannedWithPlayers(4, 1);
        tournaments.save(tournament);
        flushAndClear();

        Tournament loaded = tournaments.findByIdForUpdate(tournament.id()).orElseThrow();
        loaded.unregister(loaded.participants().get(0).playerId());
        tournaments.save(loaded);
        flushAndClear();

        assertThat(tournaments.findById(tournament.id()).orElseThrow().participants()).hasSize(3);
    }

    @Test
    void listsSummariesNewestFirst() {
        Tournament older = Tournament.plan(TournamentId.random(), "Alt", LocalDate.of(2026, 5, 1),
                TournamentConfig.defaults());
        Tournament newer = plannedWithPlayers(5, 1);
        newer.start();
        newer.startNextRound(strategy, suggester);
        tournaments.save(older);
        tournaments.save(newer);
        flushAndClear();

        List<TournamentSummary> summaries = tournaments.findAllSummaries();

        assertThat(summaries).extracting(TournamentSummary::name).containsExactly("Herbstturnier", "Alt");
        assertThat(summaries.get(0).participantCount()).isEqualTo(5);
        assertThat(summaries.get(0).roundCount()).isEqualTo(1);
        assertThat(summaries.get(0).status()).isEqualTo(TournamentStatus.RUNNING);
        assertThat(summaries.get(1).participantCount()).isZero();
        assertThat(summaries.get(1).roundCount()).isZero();
    }

    @Test
    void reportsExistenceAndMissingTournaments() {
        Tournament tournament = plannedWithPlayers(1, 1);
        tournaments.save(tournament);
        flushAndClear();

        assertThat(tournaments.exists(tournament.id())).isTrue();
        assertThat(tournaments.exists(TournamentId.random())).isFalse();
        assertThat(tournaments.findById(TournamentId.random())).isEmpty();
        assertThat(tournaments.findByIdForUpdate(TournamentId.random())).isEmpty();
    }

    // ---------------------------------------------------------------- Spieler

    @Test
    void findsPlayersByIdAndSubject() {
        Player anna = newPlayer("Anna");
        newPlayer("Ben");
        flushAndClear();

        assertThat(players.findById(anna.id())).contains(anna);
        assertThat(players.findBySubject("sub-Anna")).contains(anna);
        assertThat(players.findBySubject("unbekannt")).isEmpty();
        assertThat(players.findAllById(List.of(anna.id()))).containsOnlyKeys(anna.id());
        assertThat(players.findAll()).extracting(Player::displayName).containsExactly("Anna", "Ben");
    }

    @Test
    void updatesDisplayName() {
        Player anna = newPlayer("Anna");
        flushAndClear();

        players.save(anna.withDisplayName("Anna Schmidt"));
        flushAndClear();

        assertThat(players.findById(anna.id()).orElseThrow().displayName()).isEqualTo("Anna Schmidt");
    }

    @Test
    void rejectsSecondPlayerWithSameSubject() {
        newPlayer("Anna");

        assertThatThrownBy(() -> players.save(new Player(PlayerId.random(), "sub-Anna", "Doppelgänger")))
                .isInstanceOf(PlayerAlreadyExistsException.class);
    }
}
