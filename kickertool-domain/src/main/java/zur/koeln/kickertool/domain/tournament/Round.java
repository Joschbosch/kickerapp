package zur.koeln.kickertool.domain.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Eine Runde: alle aktiven Spieler spielen genau ein Match. Die Matches stehen in der Reihenfolge der
 * Auslosung. Die Tische werden in Wellen vergeben (siehe {@link Tournament}).
 */
public final class Round {

    private final int number;
    private final List<Match> matches;

    private Round(int number, List<Match> matches) {
        this.number = number;
        this.matches = new ArrayList<>(matches);
    }

    static Round start(int number, List<Match> matches) {
        return new Round(number, matches);
    }

    /** Rekonstruktion aus der Persistenz. */
    public static Round restore(int number, List<Match> matches) {
        return new Round(number, matches);
    }

    public int number() {
        return number;
    }

    public List<Match> matches() {
        return List.copyOf(matches);
    }

    public Optional<Match> match(MatchId id) {
        return matches.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Die Runde ist abgeschlossen, wenn alle Ergebnisse bestätigt sind. */
    public boolean isComplete() {
        return matches.stream().allMatch(Match::isConfirmed);
    }

    List<Match> queuedMatches() {
        return matches.stream().filter(m -> m.status() == MatchStatus.QUEUED).toList();
    }

    List<Match> matchesOnTable() {
        return matches.stream().filter(m -> m.status() == MatchStatus.ON_TABLE).toList();
    }
}
