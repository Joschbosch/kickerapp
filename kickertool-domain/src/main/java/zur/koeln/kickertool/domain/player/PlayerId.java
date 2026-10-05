package zur.koeln.kickertool.domain.player;

import java.util.Objects;
import java.util.UUID;

public record PlayerId(UUID value) {

    public PlayerId {
        Objects.requireNonNull(value, "value");
    }

    public static PlayerId random() {
        return new PlayerId(UUID.randomUUID());
    }

    public static PlayerId of(String value) {
        return new PlayerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
