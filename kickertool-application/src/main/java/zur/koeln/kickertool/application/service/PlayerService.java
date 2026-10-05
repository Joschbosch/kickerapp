package zur.koeln.kickertool.application.service;

import java.util.List;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.application.port.out.PlayerAlreadyExistsException;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Bewusst ohne umschließende Transaktion: Bei paralleler Erstanmeldung scheitert das Anlegen an der
 * Eindeutigkeit der {@code subject}-Kennung, danach wird der bereits angelegte Spieler gelesen.
 */
public class PlayerService implements PlayerUseCase {

    private final PlayerRepository players;

    public PlayerService(PlayerRepository players) {
        this.players = players;
    }

    @Override
    public Player provision(String subject, String displayName) {
        return players.findBySubject(subject)
                .map(existing -> existing.displayName().equals(displayName)
                        ? existing
                        : players.save(existing.withDisplayName(displayName)))
                .orElseGet(() -> create(subject, displayName));
    }

    private Player create(String subject, String displayName) {
        try {
            return players.save(new Player(PlayerId.random(), subject, displayName));
        } catch (PlayerAlreadyExistsException e) {
            return players.findBySubject(subject).orElseThrow(() -> e);
        }
    }

    @Override
    public Player getPlayer(PlayerId id) {
        return players.findById(id).orElseThrow(() -> new NotFoundException("Spieler nicht gefunden: " + id));
    }

    @Override
    public List<Player> listPlayers(Actor actor) {
        actor.requireAdmin();
        return players.findAll();
    }
}
