package zur.koeln.kickertool.adapter.in.rest.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import zur.koeln.kickertool.application.Actor;

/**
 * OpenAPI-Beschreibung der API und Swagger UI. Mit {@code springdoc.api-docs.enabled=false} und
 * {@code springdoc.swagger-ui.enabled=false} (bzw. {@code KICKERTOOL_OPENAPI_ENABLED=false}) wird beides
 * abgeschaltet, z. B. in Produktion.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER = "bearerAuth";
    private static final String OIDC = "oidc";
    private static final String PROBLEM_JSON = "application/problem+json";

    static {
        // Der angemeldete Nutzer kommt aus dem Token, er ist kein Parameter der API
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
    }

    @Bean
    OpenAPI kickertoolOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
        Components components = new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access-Token des OIDC-Providers einfügen, ohne das Präfix `Bearer`."));
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("Kickertool API")
                        .version("0.1.0")
                        .description("""
                                REST-API für Kicker-Turniere (2 gegen 2, wechselnde Partner).

                                **Anmeldung:** Registrierung und Login übernimmt der OIDC-Provider. Die API prüft nur \
                                das Access-Token. Über **Authorize** kann man sich direkt anmelden (`oidc`) oder ein \
                                Token einfügen (`bearerAuth`). Admin-Endpunkte verlangen die konfigurierte Admin-Rolle \
                                im Token, ein normaler Spieler bekommt dort 403.

                                **Fehler** kommen als `application/problem+json`.

                                **Live-Updates:** `GET /api/tournaments/{id}/events` liefert Server-Sent Events. Swagger \
                                UI kann diesen Stream nicht darstellen, dafür `curl -N` mit Bearer-Header nutzen."""))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));

        if (issuerUri != null && !issuerUri.isBlank()) {
            String discovery = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
            components.addSecuritySchemes(OIDC, new SecurityScheme()
                    .type(SecurityScheme.Type.OPENIDCONNECT)
                    .openIdConnectUrl(discovery)
                    .description("Anmeldung über den OIDC-Provider (Authorization Code mit PKCE)."));
            api.addSecurityItem(new SecurityRequirement().addList(OIDC));
        }
        return api;
    }

    /** Hängt die möglichen Fehlerantworten an jede Operation, sofern nicht selbst beschrieben. */
    @Bean
    OperationCustomizer errorResponses() {
        Schema<?> problem = new ObjectSchema()
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Conflict"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Runde 1 ist noch nicht abgeschlossen"))
                .addProperty("instance", new StringSchema());
        Content problemContent = new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(problem));

        return (Operation operation, org.springframework.web.method.HandlerMethod handlerMethod) -> {
            ApiResponses responses = operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
            responses.putIfAbsent("400", new ApiResponse()
                    .description("Ungültige Eingabe, z. B. Pflichtfeld fehlt oder Ergebnis über dem Tor-Limit")
                    .content(problemContent));
            responses.putIfAbsent("401", new ApiResponse().description("Kein oder ungültiges Token"));
            responses.putIfAbsent("403", new ApiResponse()
                    .description("Keine Berechtigung, z. B. kein Admin oder nicht Teil des Matches")
                    .content(problemContent));
            responses.putIfAbsent("404", new ApiResponse().description("Nicht gefunden").content(problemContent));
            responses.putIfAbsent("409", new ApiResponse()
                    .description("Passt nicht zum aktuellen Stand, z. B. falscher Turnierstatus oder Runde nicht abgeschlossen")
                    .content(problemContent));
            operation.setResponses(responses);
            return operation;
        };
    }
}
