package zur.koeln.kickertool.application.port.in;

import zur.koeln.kickertool.application.view.PlayerStatisticsView;
import zur.koeln.kickertool.domain.player.PlayerId;

public interface PlayerStatisticsQueries {

    /**
     * Die Statistik eines Spielers über alle seine laufenden und beendeten Turniere. Jeder angemeldete Spieler
     * darf die Statistik jedes Spielers sehen.
     *
     * @throws zur.koeln.kickertool.domain.NotFoundException wenn es den Spieler nicht gibt
     */
    PlayerStatisticsView statistics(PlayerId playerId);
}
