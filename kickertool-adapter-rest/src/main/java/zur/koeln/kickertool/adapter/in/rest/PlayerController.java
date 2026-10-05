package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.domain.player.Player;

@RestController
@RequestMapping("/api")
class PlayerController {

    private final PlayerUseCase players;

    PlayerController(PlayerUseCase players) {
        this.players = players;
    }

    /** Das eigene Profil. Legt den Spieler beim ersten Aufruf an. */
    @GetMapping("/me")
    Responses.Me me(Actor actor) {
        Player player = players.getPlayer(actor.playerId());
        return new Responses.Me(player.id().value(), player.displayName(), actor.admin());
    }

    /** Alle bekannten Spieler, nur für Admins. */
    @GetMapping("/players")
    List<Responses.Player> list(Actor actor) {
        return players.listPlayers(actor).stream().map(RestMapper::player).toList();
    }
}
