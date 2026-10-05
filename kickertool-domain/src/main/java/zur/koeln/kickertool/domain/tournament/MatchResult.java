package zur.koeln.kickertool.domain.tournament;

import java.util.Optional;

/**
 * Endstand eines Matches (ein Satz).
 *
 * @param goalsA Tore von Team A
 * @param goalsB Tore von Team B
 */
public record MatchResult(int goalsA, int goalsB) {

    public MatchResult {
        if (goalsA < 0 || goalsB < 0) {
            throw new IllegalArgumentException("Tore dürfen nicht negativ sein");
        }
    }

    /**
     * Ein Match endet bei Erreichen des Tor-Limits oder nach Zeitablauf. Daher darf kein Team mehr Tore als
     * das Limit haben, und beide Teams können das Limit nicht gleichzeitig erreicht haben.
     */
    public void validateAgainst(int goalLimit) {
        if (goalsA > goalLimit || goalsB > goalLimit) {
            throw new IllegalArgumentException("Kein Team darf mehr als " + goalLimit + " Tore haben");
        }
        if (goalsA == goalLimit && goalsB == goalLimit) {
            throw new IllegalArgumentException(
                    "Beide Teams können nicht gleichzeitig das Tor-Limit von " + goalLimit + " erreichen");
        }
    }

    public int goalsFor(Side side) {
        return side == Side.A ? goalsA : goalsB;
    }

    public int goalsAgainst(Side side) {
        return goalsFor(side.opposite());
    }

    /** Die Gewinnerseite, leer bei Unentschieden. */
    public Optional<Side> winner() {
        if (goalsA == goalsB) {
            return Optional.empty();
        }
        return Optional.of(goalsA > goalsB ? Side.A : Side.B);
    }
}
