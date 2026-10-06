package zur.koeln.kickertool.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
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
 * Der Event-Stream über echtes HTTP, so wie ihn ein Browser nutzt: Ticket holen, Stream ohne Authorization-Header
 * öffnen, Verbindung verlieren, mit {@code Last-Event-ID} neu verbinden und verpasste Events nachgeliefert bekommen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Import(EventStreamEndToEndTest.TestSecurity.class)
class EventStreamEndToEndTest {

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
    private final String admin = TestJwts.token("kc-admin", "Turnierleitung", true);

    // ---------------------------------------------------------------- Hilfen

    private record Response(int status, Map<String, Object> body) {
    }

    @SuppressWarnings("unchecked")
    private Response call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = response.body().isBlank() ? null
                : (Map<String, Object>) json.readValue(response.body(), Object.class);
        return new Response(response.statusCode(), parsed);
    }

    /** Ein geöffneter Stream, dessen Zeilen sich in {@code lines} sammeln. */
    private final class OpenStream implements AutoCloseable {
        final List<String> lines = new CopyOnWriteArrayList<>();
        final int status;
        private final Stream<String> body;

        OpenStream(String path, Map<String, String> headers) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "text/event-stream");
            headers.forEach(builder::header);
            HttpResponse<Stream<String>> response = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofLines())
                    .get();
            this.status = response.statusCode();
            this.body = response.body();
            if (status == 200) {
                Thread reader = new Thread(() -> {
                    try {
                        body.forEach(lines::add);
                    } catch (RuntimeException e) {
                        // Verbindung wurde vom Test geschlossen
                    }
                }, "e2e-sse-reader");
                reader.setDaemon(true);
                reader.start();
            }
        }

        void awaitLine(Predicate<String> condition, String description) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (lines.stream().noneMatch(condition) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertThat(lines).as(description).anyMatch(condition);
        }

        List<String> values(String field) {
            return lines.stream().filter(l -> l.startsWith(field + ":")).map(l -> l.substring(field.length() + 1))
                    .toList();
        }

        @Override
        public void close() {
            body.close();
        }
    }

    private String createTournament() throws Exception {
        Response created = call("POST", "/api/tournaments", admin,
                "{\"name\":\"Stream-Turnier\",\"date\":\"2026-10-05\"}");
        assertThat(created.status()).isEqualTo(201);
        return (String) created.body().get("id");
    }

    private void register(String tournamentId, String name) throws Exception {
        String token = TestJwts.token("sub-" + name, name, false);
        assertThat(call("GET", "/api/me", token, null).status()).isEqualTo(200);
        assertThat(call("POST", "/api/tournaments/" + tournamentId + "/participants", token, null).status())
                .isEqualTo(201);
    }

    private String ticketUrl(String tournamentId) throws Exception {
        Response ticket = call("POST", "/api/tournaments/" + tournamentId + "/events/ticket", admin, null);
        assertThat(ticket.status()).isEqualTo(200);
        assertThat(ticket.body().get("expiresInSeconds")).isEqualTo(120);
        return (String) ticket.body().get("streamUrl");
    }

    // ---------------------------------------------------------------- Tests

    @Test
    void aBrowserOpensTheStreamWithATicketLosesTheConnectionAndCatchesUp() throws Exception {
        String tournamentId = createTournament();
        String streamUrl = ticketUrl(tournamentId);

        String lastSeenId;
        try (OpenStream first = new OpenStream(streamUrl, Map.of())) {
            assertThat(first.status).as("ohne Authorization-Header, nur mit Ticket").isEqualTo(200);
            first.awaitLine(l -> l.equals("event:connected"), "connected");
            assertThat(first.lines).contains("retry:3000");

            register(tournamentId, "Anna");
            first.awaitLine(l -> l.equals("event:RANKING_CHANGED"), "Anmeldung kommt live an");
            assertThat(first.values("event")).contains("PARTICIPANTS_CHANGED", "RANKING_CHANGED");
            assertThat(first.values("id")).hasSize(2);
            lastSeenId = first.values("id").get(1);
            // Nr. 1 war das Anlegen des Turniers, die Anmeldung erzeugt Nr. 2 und 3
            assertThat(lastSeenId).matches("[0-9a-z]+-3");
        }

        // Verbindung ist weg: in der Zwischenzeit passiert einiges
        register(tournamentId, "Ben");
        assertThat(call("PUT", "/api/tournaments/" + tournamentId, admin,
                "{\"name\":\"Stream-Turnier (neu)\",\"date\":\"2026-10-05\"}").status()).isEqualTo(200);

        // Der Browser verbindet sich mit derselben Adresse neu und sendet Last-Event-ID von allein
        try (OpenStream second = new OpenStream(streamUrl, Map.of("Last-Event-ID", lastSeenId))) {
            assertThat(second.status).as("dasselbe Ticket gilt weiter").isEqualTo(200);
            second.awaitLine(l -> l.equals("event:TOURNAMENT_CHANGED"), "verpasstes Event wird nachgeliefert");

            assertThat(second.values("event")).as("genau die verpassten, in Reihenfolge")
                    .containsSubsequence("connected", "PARTICIPANTS_CHANGED", "RANKING_CHANGED", "TOURNAMENT_CHANGED")
                    .doesNotContain("RESYNC");
            assertThat(second.values("id")).hasSize(3);
            String prefix = lastSeenId.substring(0, lastSeenId.lastIndexOf('-') + 1);
            assertThat(second.values("id")).containsExactly(prefix + "4", prefix + "5", prefix + "6");

            // und ab jetzt wieder live
            register(tournamentId, "Clara");
            second.awaitLine(l -> l.equals("id:" + prefix + "7"), "danach live, ohne Doppelte");
        }
    }

    @Test
    void aNewEventSourceWithANewTicketCanCatchUpViaTheQueryParameter() throws Exception {
        String tournamentId = createTournament();
        String lastSeenId;
        try (OpenStream first = new OpenStream(ticketUrl(tournamentId), Map.of())) {
            register(tournamentId, "Anna");
            first.awaitLine(l -> l.equals("event:RANKING_CHANGED"), "Anmeldung kommt live an");
            lastSeenId = first.values("id").get(1);
        }
        register(tournamentId, "Ben");

        // Ein neues EventSource-Objekt kann keinen Header setzen: neues Ticket, Kennung in der Adresse
        try (OpenStream second = new OpenStream(ticketUrl(tournamentId) + "&lastEventId=" + lastSeenId, Map.of())) {
            assertThat(second.status).isEqualTo(200);
            second.awaitLine(l -> l.equals("event:RANKING_CHANGED"), "verpasste Events");
            assertThat(second.values("event")).contains("PARTICIPANTS_CHANGED", "RANKING_CHANGED")
                    .doesNotContain("RESYNC");
            assertThat(second.values("id")).hasSize(2);
        }
    }

    @Test
    void anUnknownEventIdAsksForAFullReload() throws Exception {
        String tournamentId = createTournament();
        register(tournamentId, "Anna");
        String streamUrl = ticketUrl(tournamentId);

        try (OpenStream stream = new OpenStream(streamUrl, Map.of("Last-Event-ID", "anderer-start-41"))) {
            assertThat(stream.status).isEqualTo(200);
            stream.awaitLine(l -> l.equals("event:RESYNC"), "RESYNC");
            assertThat(stream.values("id")).as("RESYNC trägt die aktuelle Kennung").singleElement()
                    .satisfies(id -> assertThat(id).matches("[0-9a-z]+-3"));
        }
    }

    @Test
    void theBearerTokenStillWorksForTheStream() throws Exception {
        String tournamentId = createTournament();

        try (OpenStream stream = new OpenStream("/api/tournaments/" + tournamentId + "/events",
                Map.of("Authorization", "Bearer " + admin))) {
            assertThat(stream.status).isEqualTo(200);
            stream.awaitLine(l -> l.equals("event:connected"), "connected");
        }
    }

    @Test
    void rejectsStreamsWithoutOrWithWrongCredentials() throws Exception {
        String tournamentId = createTournament();
        String otherTournament = createTournament();
        String ticketOfOther = ticketUrl(otherTournament).split("ticket=")[1];

        try (OpenStream none = new OpenStream("/api/tournaments/" + tournamentId + "/events", Map.of());
                OpenStream fake = new OpenStream("/api/tournaments/" + tournamentId + "/events?ticket=erfunden", Map.of());
                OpenStream foreign = new OpenStream(
                        "/api/tournaments/" + tournamentId + "/events?ticket=" + ticketOfOther, Map.of())) {
            assertThat(none.status).isEqualTo(401);
            assertThat(fake.status).isEqualTo(401);
            assertThat(foreign.status).as("Ticket eines anderen Turniers").isEqualTo(401);
        }
        assertThat(call("POST", "/api/tournaments/" + tournamentId + "/events/ticket", null, null).status())
                .as("Tickets gibt es nur mit Token").isEqualTo(401);
    }
}
