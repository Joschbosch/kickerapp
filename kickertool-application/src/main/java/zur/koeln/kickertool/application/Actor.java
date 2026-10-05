package zur.koeln.kickertool.application;

import java.util.Objects;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Wer eine Aktion auslöst: ein Spieler, ggf. mit Admin-Rechten. Die Adapter leiten das aus dem Login ab, die
 * Berechtigungsprüfung passiert in der Anwendungsschicht.
 */
public record Actor(PlayerId playerId, boolean admin) {

    public Actor {
        Objects.requireNonNull(playerId, "playerId");
    }

    public void requireAdmin() {
        if (!admin) {
            throw new ForbiddenException("Nur Admins dürfen das");
        }
    }

    public void requireSelfOrAdmin(PlayerId target) {
        if (!admin && !playerId.equals(target)) {
            throw new ForbiddenException("Das darf nur der Spieler selbst oder ein Admin");
        }
    }
}
