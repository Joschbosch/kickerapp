package zur.koeln.kickertool.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hält {@code docs/openapi.json} aktuell. Diese Datei ist der Vertrag für UI-Entwicklung: Daraus lassen sich Clients
 * erzeugen oder ein Mock-Server starten, ohne das Backend zu bauen. Der Test schlägt fehl, wenn die API sich geändert
 * hat, ohne dass die Datei nachgezogen wurde.
 *
 * <p>Neu erzeugen:
 * {@code ./mvnw -pl kickertool-bootstrap test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true}
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapisnapshot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/kickertool"
})
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    private static final String UPDATE_PROPERTY = "openapi.update";

    @Autowired
    MockMvc mvc;

    @Test
    void committedOpenApiFileMatchesTheApi() throws Exception {
        String actual = currentApiDescription();
        Path file = locateFile();

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }

        assertThat(file).as("docs/openapi.json fehlt. Erzeugen mit -D%s=true", UPDATE_PROPERTY).exists();
        String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(committed)
                .as("docs/openapi.json ist veraltet. Neu erzeugen: ./mvnw -pl kickertool-bootstrap test "
                        + "-Dtest=OpenApiSnapshotTest -D%s=true", UPDATE_PROPERTY)
                .isEqualTo(actual);
    }

    /** Die Beschreibung der laufenden API, stabil sortiert und ohne die vom Aufruf abhängige Server-Adresse. */
    private String currentApiDescription() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonMapper sorted = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        Map<String, Object> document = sorted.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() { });
        document.remove("servers");
        return sorted.writeValueAsString(document).replace("\r\n", "\n") + "\n";
    }

    /** Das Repo-Verzeichnis {@code docs}: je nach Startverzeichnis des Tests im Modul oder im Projektstamm. */
    private static Path locateFile() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(cwd.resolve("docs")) ? cwd.resolve("docs/openapi.json")
                : cwd.resolveSibling("docs").resolve("openapi.json");
    }
}
