package zur.koeln.kickertool.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;

public interface PlayerRepository {

    Optional<Player> findById(PlayerId id);

    Optional<Player> findBySubject(String subject);

    Map<PlayerId, Player> findAllById(Collection<PlayerId> ids);

    List<Player> findAll();

    /**
     * Speichert den Spieler.
     *
     * @throws PlayerAlreadyExistsException wenn für die {@code subject}-Kennung parallel ein Spieler angelegt wurde
     */
    Player save(Player player);
}
