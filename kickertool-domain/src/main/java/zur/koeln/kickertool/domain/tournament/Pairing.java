package zur.koeln.kickertool.domain.tournament;

import java.util.Objects;

/** Das Ergebnis der Auslosung für ein Match: zwei Teams, die gegeneinander spielen. */
public record Pairing(Team teamA, Team teamB) {

    public Pairing {
        Objects.requireNonNull(teamA, "teamA");
        Objects.requireNonNull(teamB, "teamB");
    }
}
