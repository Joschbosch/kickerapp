package zur.koeln.kickertool.domain.tournament;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Wählt den Kandidaten, dessen Rang dem Durchschnittsrang der echten Spieler des Matches am nächsten liegt. */
public class NearestRankStandInSuggester implements StandInSuggester {

    @Override
    public Optional<PlayerId> suggest(Match match, Ranking ranking, Collection<PlayerId> candidates) {
        List<Integer> matchRanks = match.realPlayers().stream()
                .map(ranking::rankOf)
                .flatMap(Optional::stream)
                .toList();
        double target = matchRanks.stream().mapToInt(Integer::intValue).average().orElse(1);

        return candidates.stream().min(Comparator
                .comparingDouble((PlayerId candidate) -> Math.abs(ranking.rankOf(candidate).orElse(Integer.MAX_VALUE) - target))
                // deterministisch bei Gleichstand
                .thenComparing(candidate -> candidate.value()));
    }
}
