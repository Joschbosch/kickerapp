package zur.koeln.kickertool.domain.tournament;

import java.util.List;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Die Rangliste eines Turniers, sortiert nach Rang. */
public record Ranking(List<RankingEntry> entries) {

    public Ranking {
        entries = List.copyOf(entries);
    }

    public Optional<RankingEntry> entryOf(PlayerId playerId) {
        return entries.stream().filter(e -> e.playerId().equals(playerId)).findFirst();
    }

    public Optional<Integer> rankOf(PlayerId playerId) {
        return entryOf(playerId).map(RankingEntry::rank);
    }
}
