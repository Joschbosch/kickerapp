package zur.koeln.kickertool.domain.tournament;

import java.util.Objects;
import java.util.UUID;

public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "value");
    }

    public static MatchId random() {
        return new MatchId(UUID.randomUUID());
    }

    public static MatchId of(String value) {
        return new MatchId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
