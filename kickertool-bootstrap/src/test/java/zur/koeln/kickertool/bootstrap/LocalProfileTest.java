package zur.koeln.kickertool.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.json.JsonMapper;

/**
 * Der lokale Testmodus ({@code local}): H2, simulierter OIDC-Provider, Demo-Turnier. Prüft den Weg, den Swagger UI
 * nimmt: Discovery lesen, per Password-Flow ein Token holen, damit die API aufrufen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local",
        // Der Test läuft auf einem Zufallsport, im echten Start steht hier der konfigurierte Port
        "kickertool.local.issuer=http://localhost:8080/mock-oidc",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/mock-oidc"
})
class LocalProfileTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Response(int status, Object body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> obj() {
            return (Map<String, Object>) body;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list() {
            return (List<Map<String, Object>>) body;
        }
    }

    private Response send(HttpRequest.Builder builder, String token) {
        try {
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Object parsed = response.body().isBlank() ? null : json.readValue(response.body(), Object.class);
            return new Response(response.statusCode(), parsed);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private Response get(String path, String token) {
        return send(request(path), token);
    }

    private Response login(String username, String password) {
        String form = "grant_type=password&client_id=kickertool-app&username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return send(request("/mock-oidc/token").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), null);
    }

    private String tokenOf(String username) {
        Response response = login(username, username);
        assertThat(response.status()).isEqualTo(200);
        return (String) response.obj().get("access_token");
    }

    @Test
    void publishesDiscoveryDocumentAndKeysWithoutLogin() {
        Response discovery = get("/mock-oidc/.well-known/openid-configuration", null);
        assertThat(discovery.status()).isEqualTo(200);
        assertThat(discovery.obj().get("token_endpoint")).isEqualTo("http://localhost:8080/mock-oidc/token");
        assertThat(discovery.obj().get("grant_types_supported")).isEqualTo(List.of("password"));

        Response jwks = get("/mock-oidc/jwks", null);
        assertThat(jwks.status()).isEqualTo(200);
        assertThat(jwks.obj().get("keys")).asList().hasSize(1);
        assertThat(jwks.obj().toString()).doesNotContain("\"d\"");
    }

    @Test
    void logsInWithPasswordFlowAndCallsTheApi() {
        Response login = login("anna", "anna");
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.obj().get("token_type")).isEqualTo("Bearer");

        Response me = get("/api/me", (String) login.obj().get("access_token"));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.obj().get("displayName")).isEqualTo("Anna Beispiel");
        assertThat(me.obj().get("admin")).isEqualTo(false);
    }

    @Test
    void adminUserGetsTheAdminRole() {
        Response me = get("/api/me", tokenOf("admin"));

        assertThat(me.obj().get("admin")).isEqualTo(true);
        assertThat(get("/api/players", tokenOf("admin")).status()).isEqualTo(200);
        assertThat(get("/api/players", tokenOf("anna")).status()).isEqualTo(403);
    }

    @Test
    void rejectsWrongPasswordAndOtherGrantTypes() {
        assertThat(login("anna", "falsch").status()).isEqualTo(401);
        assertThat(login("niemand", "niemand").status()).isEqualTo(401);

        String form = "grant_type=client_credentials&client_id=kickertool-app";
        Response other = send(request("/mock-oidc/token").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), null);
        assertThat(other.status()).isEqualTo(400);
    }

    @Test
    void theApiStillRejectsRequestsWithoutOrWithForgedTokens() {
        assertThat(get("/api/tournaments", null).status()).isEqualTo(401);
        assertThat(get("/api/tournaments", "eyJhbGciOiJSUzI1NiJ9.e30.ungueltig").status()).isEqualTo(401);
    }

    @Test
    void createsDemoTournamentWithAllPlayersRegistered() {
        String admin = tokenOf("admin");

        Response tournaments = get("/api/tournaments", admin);
        assertThat(tournaments.status()).isEqualTo(200);
        assertThat(tournaments.list()).singleElement().satisfies(t -> {
            assertThat(t.get("name")).isEqualTo("Demo-Turnier");
            assertThat(t.get("status")).isEqualTo("PLANNED");
            assertThat(t.get("participantCount")).isEqualTo(10);
        });

        // Spieler aus dem Token sind dieselben, die das Demo-Turnier angemeldet hat
        Object tournamentId = tournaments.list().get(0).get("id");
        Response mine = get("/api/tournaments/" + tournamentId, tokenOf("ben"));
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.obj().get("participants")).asList().hasSize(10);
        Response me = get("/api/me", tokenOf("ben"));
        assertThat(mine.obj().get("participants").toString()).contains((String) me.obj().get("id"));
    }

    @Test
    void swaggerUiOffersTheSimulatedLogin() {
        Response docs = get("/v3/api-docs", null);
        assertThat(docs.status()).isEqualTo(200);
        Map<?, ?> schemes = (Map<?, ?>) ((Map<?, ?>) docs.obj().get("components")).get("securitySchemes");
        assertThat(((Map<?, ?>) schemes.get("oidc")).get("openIdConnectUrl"))
                .isEqualTo("http://localhost:8080/mock-oidc/.well-known/openid-configuration");
    }
}
