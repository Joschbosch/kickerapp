package zur.koeln.kickertool.adapter.in.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/** Eine UI auf einer anderen Adresse darf die API nur aufrufen, wenn ihre Adresse freigegeben ist. */
@TestPropertySource(properties = "kickertool.cors.allowed-origins=http://localhost:5173,https://*.kicker.example")
class CorsTest extends ControllerTestBase {

    private static final String UI = "http://localhost:5173";

    @Test
    void allowsPreflightFromAConfiguredOriginWithoutAToken() throws Exception {
        mvc.perform(options("/api/tournaments")
                        .header(HttpHeaders.ORIGIN, UI)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
                // Spring gibt die angefragten Header in der Schreibweise der Anfrage zurück
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("authorization")));
    }

    @Test
    void allowsWildcardPatternsForSubdomains() throws Exception {
        mvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "https://turnier.kicker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://turnier.kicker.example"));
    }

    @Test
    void rejectsPreflightFromAnUnknownOrigin() throws Exception {
        mvc.perform(options("/api/tournaments")
                        .header(HttpHeaders.ORIGIN, "http://boese.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void answersTheRealRequestWithTheOriginHeaderToo() throws Exception {
        when(queries.listTournaments()).thenReturn(List.of());

        mvc.perform(get("/api/tournaments").header(HttpHeaders.ORIGIN, UI).with(user()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI));
    }

    @Test
    void doesNotAllowCredentialsBecauseTheApiUsesBearerTokens() throws Exception {
        mvc.perform(options("/api/tournaments")
                        .header(HttpHeaders.ORIGIN, UI)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void stillRequiresATokenForTheActualCall() throws Exception {
        mvc.perform(get("/api/tournaments").header(HttpHeaders.ORIGIN, UI))
                .andExpect(status().isUnauthorized());
    }
}
