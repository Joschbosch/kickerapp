package zur.koeln.kickertool.adapter.in.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Ohne Konfiguration ist die API aus dem Browser nur von derselben Adresse aus erreichbar. */
class CorsDisabledByDefaultTest extends ControllerTestBase {

    @Test
    void rejectsPreflightWhenNoOriginIsConfigured() throws Exception {
        mvc.perform(options("/api/tournaments")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
