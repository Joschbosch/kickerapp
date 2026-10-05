package zur.koeln.kickertool.domain.player;

import java.util.Objects;

/**
 * Ein App-weit bekannter Spieler. Wird beim ersten Request mit gültigem OIDC-Token angelegt.
 *
 * @param subject die {@code sub}-Kennung des OIDC-Providers
 */
public record Player(PlayerId id, String subject, String displayName) {

    public Player {
        Objects.requireNonNull(id, "id");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }

    public Player withDisplayName(String newDisplayName) {
        return new Player(id, subject, newDisplayName);
    }
}
