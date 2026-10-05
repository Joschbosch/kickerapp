package zur.koeln.kickertool.domain.tournament;

import java.util.Objects;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Ein echter Turnierspieler. Dessen Ergebnis zählt für die Rangliste. */
public record PlayerSlot(PlayerId playerId) implements Slot {

    public PlayerSlot {
        Objects.requireNonNull(playerId, "playerId");
    }
}
