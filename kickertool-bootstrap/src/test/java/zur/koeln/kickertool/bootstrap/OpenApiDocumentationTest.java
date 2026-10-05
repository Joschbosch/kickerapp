package zur.koeln.kickertool.bootstrap;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Die OpenAPI-Beschreibung und Swagger UI sind ohne Token erreichbar, die API selbst bleibt geschützt. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/kickertool"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void describesAllEndpointsWithoutRequiringAToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Kickertool API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/me")))
                .andExpect(jsonPath("$.paths", hasKey("/api/players")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/config")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/start")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/rounds")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/rounds/current")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/finish")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{id}/ranking")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/participants")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/participants/{playerId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/participants/{playerId}/status")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/matches/mine")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/matches/{matchId}/result-proposal")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/matches/{matchId}/result-proposal/confirmation")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/matches/{matchId}/result-proposal/rejection")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/matches/{matchId}/result")))
                .andExpect(jsonPath("$.paths", hasKey("/api/tournaments/{tournamentId}/events")));
    }

    @Test
    void offersBearerTokenAndOidcLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.oidc.type").value("openIdConnect"))
                .andExpect(jsonPath("$.components.securitySchemes.oidc.openIdConnectUrl")
                        .value("http://localhost:8180/realms/kickertool/.well-known/openid-configuration"))
                .andExpect(jsonPath("$.security[*]", containsInAnyOrder(
                        java.util.Map.of("bearerAuth", java.util.List.of()),
                        java.util.Map.of("oidc", java.util.List.of()))));
    }

    @Test
    void documentsErrorResponsesAndGroupsByTag() throws Exception {
        String resultProposal = "$.paths['/api/tournaments/{tournamentId}/matches/{matchId}/result-proposal'].post";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(resultProposal + ".responses", hasKey("200")))
                .andExpect(jsonPath(resultProposal + ".responses", hasKey("400")))
                .andExpect(jsonPath(resultProposal + ".responses", hasKey("401")))
                .andExpect(jsonPath(resultProposal + ".responses", hasKey("403")))
                .andExpect(jsonPath(resultProposal + ".responses", hasKey("409")))
                .andExpect(jsonPath(resultProposal + ".responses['409'].content", hasKey("application/problem+json")))
                .andExpect(jsonPath(resultProposal + ".tags[0]").value("Matches und Ergebnisse"))
                .andExpect(jsonPath(resultProposal + ".summary").value("Ergebnis eintragen"))
                .andExpect(jsonPath("$.paths['/api/tournaments'].post.tags[0]").value("Turniere"))
                .andExpect(jsonPath("$.paths['/api/tournaments/{id}/ranking'].get.tags[0]")
                        .value("Rangliste und Runden"));
    }

    @Test
    void streamEndpointIsDescribedAsEventStream() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/tournaments/{tournamentId}/events'].get.responses['200'].content",
                        hasKey("text/event-stream")));
    }

    @Test
    void theLoggedInPlayerIsNotAnApiParameter() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(not(containsString("\"actor\""))));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistAuthorization").value(true));
    }

    @Test
    void theApiItselfStaysProtected() throws Exception {
        mvc.perform(get("/api/tournaments")).andExpect(status().isUnauthorized());
    }
}
