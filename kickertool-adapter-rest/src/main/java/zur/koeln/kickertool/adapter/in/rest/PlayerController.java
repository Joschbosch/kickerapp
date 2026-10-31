package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.PlayerStatisticsQueries;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.domain.player.Player;

@RestController
@RequestMapping("/api")
@Tag(name = "Spieler", description = "Das eigene Profil und die bekannten Spieler")
class PlayerController {

    private final PlayerUseCase players;
    private final PlayerStatisticsQueries statistics;

    PlayerController(PlayerUseCase players, PlayerStatisticsQueries statistics) {
        this.players = players;
        this.statistics = statistics;
    }

    @Operation(summary = "Eigenes Profil",
            description = "Liefert den angemeldeten Spieler und ob er Admin ist. Beim ersten Aufruf mit einem "
                    + "gültigen Token wird der Spieler automatisch angelegt.")
    @GetMapping("/me")
    Responses.Me me(Actor actor) {
        Player player = players.getPlayer(actor.playerId());
        return new Responses.Me(player.id().value(), player.displayName(), actor.admin());
    }

    @Operation(summary = "Statistik eines Spielers",
            description = "Die Statistik über alle laufenden und beendeten Turniere des Spielers: Summen (Turniere, "
                    + "Spiele, Siege, Tore, Punkte, Titel) und der Stand in jedem Turnier. Jeder angemeldete Spieler "
                    + "darf die Statistik jedes Spielers sehen. Geplante Turniere zählen nicht, Ergebnisse nur "
                    + "bestätigt. Für die eigene Statistik kann `me` als ID stehen.")
    @GetMapping("/players/{playerId}/statistics")
    Responses.PlayerStatistics statistics(Actor actor,
            @Parameter(description = PathPlayer.HELP) @PathVariable String playerId) {
        return RestMapper.statistics(statistics.statistics(PathPlayer.resolve(playerId, actor)));
    }

    @Operation(summary = "Alle bekannten Spieler",
            description = "**Nur Admin.** Z. B. um einen Spieler per ID zu einem Turnier anzumelden. Bekannt sind "
                    + "nur Spieler, die sich schon einmal angemeldet haben.")
    @GetMapping("/players")
    List<Responses.Player> list(Actor actor) {
        return players.listPlayers(actor).stream().map(RestMapper::player).toList();
    }
}
