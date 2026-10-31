package zur.koeln.kickertool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.application.view.PlayerStatisticsView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.statistics.TournamentStanding;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

class PlayerStatisticsServiceTest {

    private final InMemoryRepositories.Players players = new InMemoryRepositories.Players();
    private final InMemoryRepositories.Tournaments tournaments = new InMemoryRepositories.Tournaments();
    private final PlayerStatisticsService service = new PlayerStatisticsService(players, tournaments);

    private Player player(String name) {
        Player player = new Player(PlayerId.random(), "sub-" + name, name);
        players.save(player);
        return player;
    }

    private Tournament tournament(String name, LocalDate date, boolean started, Player... participants) {
        Tournament tournament = Tournament.plan(TournamentId.random(), name, date, TournamentConfig.defaults());
        for (Player participant : participants) {
            tournament.register(participant.id(), Instant.parse("2026-10-01T10:00:00Z"));
        }
        if (started) {
            tournament.start();
        }
        tournaments.save(tournament);
        return tournament;
    }

    @Test
    void collectsTheTournamentsOfThePlayerAndNoOthers() {
        Player anna = player("Anna");
        Player ben = player("Ben");
        tournament("Mit Anna", LocalDate.of(2026, 9, 1), true, anna, ben);
        tournament("Nur Ben", LocalDate.of(2026, 9, 2), true, ben);
        tournament("Noch geplant", LocalDate.of(2026, 9, 3), false, anna);

        PlayerStatisticsView view = service.statistics(anna.id());

        assertThat(view.player()).isEqualTo(anna);
        assertThat(view.statistics().tournaments()).extracting(TournamentStanding::name).containsExactly("Mit Anna");
        assertThat(view.statistics().totals().tournamentCount()).isEqualTo(1);
    }

    @Test
    void aPlayerWithoutTournamentsHasAnEmptyStatistic() {
        Player anna = player("Anna");

        PlayerStatisticsView view = service.statistics(anna.id());

        assertThat(view.statistics().tournaments()).isEmpty();
        assertThat(view.statistics().totals().tournamentCount()).isZero();
    }

    @Test
    void anUnknownPlayerIsNotFound() {
        assertThatThrownBy(() -> service.statistics(PlayerId.random())).isInstanceOf(NotFoundException.class);
    }
}
