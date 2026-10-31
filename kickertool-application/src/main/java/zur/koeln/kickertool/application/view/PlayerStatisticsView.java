package zur.koeln.kickertool.application.view;

import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.statistics.PlayerStatistics;

/** Die Statistik eines Spielers samt Spielerdaten (Name). Lese-Modell für die Adapter. */
public record PlayerStatisticsView(Player player, PlayerStatistics statistics) {
}
