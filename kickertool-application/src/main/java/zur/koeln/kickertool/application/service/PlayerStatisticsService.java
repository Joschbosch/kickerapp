package zur.koeln.kickertool.application.service;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.port.in.PlayerStatisticsQueries;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.PlayerStatisticsView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.statistics.PlayerStatistics;

@Transactional(readOnly = true)
public class PlayerStatisticsService implements PlayerStatisticsQueries {

    private final PlayerRepository players;
    private final TournamentRepository tournaments;

    public PlayerStatisticsService(PlayerRepository players, TournamentRepository tournaments) {
        this.players = players;
        this.tournaments = tournaments;
    }

    @Override
    public PlayerStatisticsView statistics(PlayerId playerId) {
        Player player = players.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Spieler nicht gefunden: " + playerId));
        return new PlayerStatisticsView(player,
                PlayerStatistics.of(playerId, tournaments.findAllByParticipant(playerId)));
    }
}
