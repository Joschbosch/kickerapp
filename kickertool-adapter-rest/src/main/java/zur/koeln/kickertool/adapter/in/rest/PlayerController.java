package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.domain.player.Player;

@RestController
@RequestMapping("/api")
@Tag(name = "Spieler", description = "Das eigene Profil und die bekannten Spieler")
class PlayerController {

    private final PlayerUseCase players;

    PlayerController(PlayerUseCase players) {
        this.players = players;
    }

    @Operation(summary = "Eigenes Profil",
            description = "Liefert den angemeldeten Spieler und ob er Admin ist. Beim ersten Aufruf mit einem "
                    + "gültigen Token wird der Spieler automatisch angelegt.")
    @GetMapping("/me")
    Responses.Me me(Actor actor) {
        Player player = players.getPlayer(actor.playerId());
        return new Responses.Me(player.id().value(), player.displayName(), actor.admin());
    }

    @Operation(summary = "Alle bekannten Spieler",
            description = "**Nur Admin.** Z. B. um einen Spieler per ID zu einem Turnier anzumelden. Bekannt sind "
                    + "nur Spieler, die sich schon einmal angemeldet haben.")
    @GetMapping("/players")
    List<Responses.Player> list(Actor actor) {
        return players.listPlayers(actor).stream().map(RestMapper::player).toList();
    }
}
