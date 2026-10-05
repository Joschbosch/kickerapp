package zur.koeln.kickertool.local;

import java.util.List;
import java.util.Optional;

/** Die Testnutzer des simulierten Providers. Das Passwort ist jeweils der Benutzername. */
final class MockUsers {

    record MockUser(String username, String displayName, boolean admin) {

        /** Die {@code sub}-Kennung im Token, so wird der Spieler in der App wiedererkannt. */
        String subject() {
            return "mock-" + username;
        }

        String email() {
            return username + "@kicker.local";
        }
    }

    static final List<MockUser> ALL = List.of(
            new MockUser("admin", "Turnier Leitung", true),
            new MockUser("anna", "Anna Beispiel", false),
            new MockUser("ben", "Ben Beispiel", false),
            new MockUser("clara", "Clara Beispiel", false),
            new MockUser("david", "David Beispiel", false),
            new MockUser("emma", "Emma Beispiel", false),
            new MockUser("felix", "Felix Beispiel", false),
            new MockUser("greta", "Greta Beispiel", false),
            new MockUser("hans", "Hans Beispiel", false),
            new MockUser("ida", "Ida Beispiel", false),
            new MockUser("jonas", "Jonas Beispiel", false));

    private MockUsers() {
    }

    static Optional<MockUser> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        return ALL.stream().filter(u -> u.username().equals(username) && u.username().equals(password)).findFirst();
    }

    static List<MockUser> players() {
        return ALL.stream().filter(u -> !u.admin()).toList();
    }

    static MockUser admin() {
        return ALL.stream().filter(MockUser::admin).findFirst().orElseThrow();
    }
}
