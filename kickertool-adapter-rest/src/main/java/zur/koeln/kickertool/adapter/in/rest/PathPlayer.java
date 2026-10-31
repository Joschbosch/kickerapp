package zur.koeln.kickertool.adapter.in.rest;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.domain.player.PlayerId;

/** Die Spieler-ID im Pfad: eine UUID oder {@code me} für den Aufrufer. */
final class PathPlayer {

    static final String HELP = "Spieler-ID oder `me` für den Aufrufer";

    private static final String ME = "me";

    private PathPlayer() {
    }

    static PlayerId resolve(String value, Actor actor) {
        return ME.equals(value) ? actor.playerId() : PlayerId.of(value);
    }
}
