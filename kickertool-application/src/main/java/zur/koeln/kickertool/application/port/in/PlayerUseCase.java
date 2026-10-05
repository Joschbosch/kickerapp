package zur.koeln.kickertool.application.port.in;

import java.util.List;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;

public interface PlayerUseCase {

    /**
     * Liefert den Spieler zur OIDC-Kennung und legt ihn beim ersten Login an. Ein geänderter Anzeigename
     * wird übernommen.
     */
    Player provision(String subject, String displayName);

    Player getPlayer(PlayerId id);

    /** Alle bekannten Spieler, nur für Admins (z. B. um Spieler anzumelden). */
    List<Player> listPlayers(Actor actor);
}
