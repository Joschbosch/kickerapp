package zur.koeln.kickertool.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import tools.jackson.databind.json.JsonMapper;

/**
 * Spielt ein komplettes Turnier über HTTP gegen die echte Anwendung (H2 statt PostgreSQL, Test-Tokens statt
 * OIDC-Provider): Anmeldung, Runden, Tische in Wellen, Ergebnisse mit Bestätigung, Pause, Rangliste und
 * Live-Updates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Import(KickertoolEndToEndTest.TestSecurity.class)
class KickertoolEndToEndTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        JwtDecoder jwtDecoder() {
            return TestJwts.decoder();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String adminToken = TestJwts.token("kc-admin", "Turnierleiter", true);

    // ---------------------------------------------------------------- HTTP-Hilfen

    record Response(int status, Object body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> obj() {
            return (Map<String, Object>) body;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list() {
            return (List<Map<String, Object>>) body;
        }
    }

    private Response call(String method, String path, String token, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Object parsed = response.body().isBlank() ? null : json.readValue(response.body(), Object.class);
            return new Response(response.statusCode(), parsed);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private Response get(String path, String token) {
        return call("GET", path, token, null);
    }

    private Response post(String path, String token, String body) {
        return call("POST", path, token, body);
    }

    private Response put(String path, String token, String body) {
        return call("PUT", path, token, body);
    }

    private static String str(Object value) {
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> members(Map<String, Object> team) {
        return (List<Map<String, Object>>) team.get("members");
    }

    // ---------------------------------------------------------------- Spieler

    private record User(String name, String token, String id) {
    }

    private User registerUser(int index) {
        String name = "Spieler " + index;
        String token = TestJwts.token("e2e-user-" + index, name, false);
        Response me = get("/api/me", token);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.obj().get("displayName")).isEqualTo(name);
        assertThat(me.obj().get("admin")).isEqualTo(false);
        return new User(name, token, str(me.obj().get("id")));
    }

    /** Die Nummern aller Runden, in denen der Spieler an einem Tisch stand (auch als Einspringer). */
    private List<Object> roundsPlayedBy(String tournament, User user) {
        return get(tournament + "/matches/mine", user.token()).list().stream().map(m -> m.get("round")).toList();
    }

    /** Der erste am Tisch, der Ergebnisse eintragen darf: ein echter Spieler oder ein Einspringer. */
    private static String firstActor(Map<String, Object> team) {
        for (Map<String, Object> member : members(team)) {
            Object player = "PLAYER".equals(member.get("type")) ? member.get("player") : member.get("standIn");
            if (player != null) {
                return str(((Map<?, ?>) player).get("id"));
            }
        }
        return null;
    }

    /** Spielt die laufende Runde komplett durch, Welle für Welle. Team A gewinnt mit 10:6. */
    private int playCurrentRound(String tournamentId, Map<String, User> usersById) {
        int played = 0;
        for (int guard = 0; guard < 20; guard++) {
            Response round = get("/api/tournaments/" + tournamentId + "/rounds/current", adminToken);
            assertThat(round.status()).isEqualTo(200);
            if (Boolean.TRUE.equals(round.obj().get("complete"))) {
                return played;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) round.obj().get("matches");
            for (Map<String, Object> match : matches) {
                if (!"ON_TABLE".equals(match.get("status"))) {
                    continue;
                }
                String matchPath = "/api/tournaments/" + tournamentId + "/matches/" + match.get("id");
                @SuppressWarnings("unchecked")
                String enteredBy = firstActor((Map<String, Object>) match.get("teamA"));
                @SuppressWarnings("unchecked")
                String confirmedBy = firstActor((Map<String, Object>) match.get("teamB"));
                if (enteredBy == null || confirmedBy == null) {
                    // Nur Dritte als Einspringer: der Admin legt das Ergebnis fest
                    assertThat(put(matchPath + "/result", adminToken, "{\"goalsA\":10,\"goalsB\":6}").status())
                            .isEqualTo(200);
                } else {
                    assertThat(post(matchPath + "/result-proposal", usersById.get(enteredBy).token(),
                            "{\"goalsA\":10,\"goalsB\":6}").status()).isEqualTo(200);
                    Response confirmed = post(matchPath + "/result-proposal/confirmation",
                            usersById.get(confirmedBy).token(), null);
                    assertThat(confirmed.status()).isEqualTo(200);
                    assertThat(confirmed.obj().get("status")).isEqualTo("CONFIRMED");
                }
                played++;
            }
        }
        throw new AssertionError("Runde wurde nicht fertig");
    }

    // ---------------------------------------------------------------- Test

    @Test
    void playsACompleteTournamentWithTenPlayersTwoTablesAndADummyMatch() throws Exception {
        // Live-Updates abonnieren, sobald es ein Turnier gibt
        Response planned = post("/api/tournaments", adminToken, """
                {"name": "E2E-Turnier", "date": "2026-10-05",
                 "config": {"tableCount": 2, "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                            "goalLimit": 10, "matchMinutes": 5, "plannedRounds": 3, "randomRounds": 1}}""");
        assertThat(planned.status()).isEqualTo(201);
        String tournamentId = str(planned.obj().get("id"));
        String tournament = "/api/tournaments/" + tournamentId;
        assertThat(planned.obj().get("status")).isEqualTo("PLANNED");

        List<String> sseLines = new CopyOnWriteArrayList<>();
        HttpRequest sse = HttpRequest.newBuilder(URI.create("http://localhost:" + port + tournament + "/events"))
                .header("Authorization", "Bearer " + adminToken).header("Accept", "text/event-stream").build();
        HttpResponse<Stream<String>> stream = http.sendAsync(sse, HttpResponse.BodyHandlers.ofLines()).get();
        assertThat(stream.statusCode()).isEqualTo(200);
        Thread reader = new Thread(() -> {
            try {
                stream.body().forEach(sseLines::add);
            } catch (RuntimeException e) {
                // Verbindung wurde vom Test geschlossen
            }
        }, "e2e-sse-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            // 10 Spieler registrieren sich im OIDC (hier: Token) und melden sich zum Turnier an
            Map<String, User> usersById = new HashMap<>();
            List<User> users = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                User user = registerUser(i);
                users.add(user);
                usersById.put(user.id(), user);
                assertThat(post(tournament + "/participants", user.token(), null).status()).isEqualTo(201);
            }
            assertThat(post(tournament + "/participants", users.get(0).token(), null).status())
                    .as("doppelte Anmeldung").isEqualTo(409);
            assertThat(get(tournament, adminToken).obj().get("participants")).asList().hasSize(10);

            // Berechtigungen
            assertThat(get("/api/tournaments", null).status()).isEqualTo(401);
            assertThat(post(tournament + "/start", users.get(0).token(), null).status()).isEqualTo(403);
            assertThat(post(tournament + "/rounds", adminToken, null).status()).as("noch nicht gestartet").isEqualTo(409);
            assertThat(get("/api/players", users.get(0).token()).status()).isEqualTo(403);
            assertThat(get("/api/players", adminToken).list()).hasSizeGreaterThanOrEqualTo(11);

            // Turnier und erste Runde starten: 3 Matches (10 Spieler + 2 Dummys), 2 Tische
            assertThat(post(tournament + "/start", adminToken, null).status()).isEqualTo(200);
            assertThat(post(tournament + "/rounds", users.get(0).token(), null).status())
                    .as("Runden startet nur der Admin").isEqualTo(403);
            Response round1 = post(tournament + "/rounds", adminToken, null);
            assertThat(round1.status()).isEqualTo(201);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches =
                    (List<Map<String, Object>>) ((Map<String, Object>) round1.obj().get("currentRound")).get("matches");
            assertThat(matches).extracting(m -> m.get("status")).containsExactly("ON_TABLE", "ON_TABLE", "QUEUED");
            assertThat(matches).extracting(m -> m.get("table")).containsExactly(1, 2, null);

            assertThat(post(tournament + "/rounds", adminToken, null).status())
                    .as("Runde 1 ist nicht abgeschlossen").isEqualTo(409);

            // Falsches Ergebnis: über dem Limit. Fremder Spieler. Dann richtig durchspielen.
            @SuppressWarnings("unchecked")
            String firstEntered = firstActor((Map<String, Object>) matches.get(0).get("teamA"));
            String firstMatch = tournament + "/matches/" + matches.get(0).get("id");
            assertThat(post(firstMatch + "/result-proposal", usersById.get(firstEntered).token(),
                    "{\"goalsA\":11,\"goalsB\":2}").status()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            String outsider = firstActor((Map<String, Object>) matches.get(1).get("teamA"));
            assertThat(post(firstMatch + "/result-proposal", usersById.get(outsider).token(),
                    "{\"goalsA\":10,\"goalsB\":2}").status()).isEqualTo(403);

            assertThat(playCurrentRound(tournamentId, usersById)).isEqualTo(3);

            // Rangliste: jeder Spieler hat genau ein Match, 5 Gewinner mit 3 Punkten
            Response ranking = get(tournament + "/ranking", users.get(0).token());
            assertThat(ranking.list()).hasSize(10);
            assertThat(ranking.list()).allSatisfy(e -> assertThat(e.get("matchesPlayed")).isEqualTo(1));
            assertThat(ranking.list().stream().filter(e -> Integer.valueOf(3).equals(e.get("points"))).count())
                    .isEqualTo(5);
            assertThat(ranking.list().get(0).get("rank")).isEqualTo(1);
            assertThat(ranking.list().get(0).get("goalDifference")).isEqualTo(4);

            // Admin korrigiert ein Ergebnis nachträglich zu einem Unentschieden
            String matchToCorrect = tournament + "/matches/" + matches.get(0).get("id");
            Response corrected = put(matchToCorrect + "/result", adminToken, "{\"goalsA\":5,\"goalsB\":5}");
            assertThat(corrected.status()).isEqualTo(200);
            assertThat(((Map<?, ?>) corrected.obj().get("result")).get("source")).isEqualTo("ADMIN");
            assertThat(get(tournament + "/ranking", adminToken).list().stream()
                    .filter(e -> Integer.valueOf(1).equals(e.get("points"))).count()).isEqualTo(4);

            // Runde 2, ein Spieler pausiert währenddessen, wird erst in Runde 3 nicht mehr zugelost
            assertThat(post(tournament + "/rounds", adminToken, null).status()).isEqualTo(201);
            User pausing = users.get(9);
            assertThat(put(tournament + "/participants/me/status", pausing.token(), "{\"status\":\"PAUSED\"}").status())
                    .isEqualTo(200);
            assertThat(roundsPlayedBy(tournament, pausing)).as("Runde 2 spielt er noch normal mit").contains(2);
            assertThat(playCurrentRound(tournamentId, usersById)).isEqualTo(3);

            assertThat(post(tournament + "/rounds", adminToken, null).status()).isEqualTo(201);
            assertThat(roundsPlayedBy(tournament, pausing)).as("Runde 3 ohne ihn").doesNotContain(3);
            assertThat(get(tournament + "/ranking", adminToken).list())
                    .filteredOn(e -> "PAUSED".equals(e.get("status"))).hasSize(1);
            assertThat(get(tournament, adminToken).obj().get("roundCount")).isEqualTo(3);
            assertThat(playCurrentRound(tournamentId, usersById)).isEqualTo(3);

            // Beenden
            assertThat(post(tournament + "/finish", adminToken, null).status()).isEqualTo(200);
            assertThat(get(tournament, adminToken).obj().get("status")).isEqualTo("FINISHED");
            assertThat(get("/api/tournaments", adminToken).list()).hasSize(1);

            // Live-Updates sind angekommen
            long deadline = System.currentTimeMillis() + 5_000;
            while (!sseLines.contains("event:TOURNAMENT_CHANGED") && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(sseLines).contains("event:connected", "event:ROUND_STARTED", "event:MATCHES_CHANGED",
                    "event:RANKING_CHANGED", "event:PARTICIPANTS_CHANGED", "event:TOURNAMENT_CHANGED");
        } finally {
            stream.body().close();
        }
    }
}
