package zur.koeln.kickertool.domain.tournament;

import java.util.Objects;
import java.util.UUID;

public record TournamentId(UUID value) {

    public TournamentId {
        Objects.requireNonNull(value, "value");
    }

    public static TournamentId random() {
        return new TournamentId(UUID.randomUUID());
    }

    public static TournamentId of(String value) {
        return new TournamentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
