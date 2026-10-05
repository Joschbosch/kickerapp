package zur.koeln.kickertool.application.view;

import java.util.Map;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Tournament;

/** Ein Turnier mit den Spielerdaten (Namen), die es referenziert. Lese-Modell für die Adapter. */
public record TournamentView(Tournament tournament, Map<PlayerId, Player> players) {

    public TournamentView {
        players = Map.copyOf(players);
    }

    public Optional<Player> player(PlayerId id) {
        return Optional.ofNullable(players.get(id));
    }
}
