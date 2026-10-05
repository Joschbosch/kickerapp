package zur.koeln.kickertool.local;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.local.MockUsers.MockUser;

/**
 * Ein minimaler OIDC-Provider für lokale Tests: Discovery-Dokument, Schlüssel und ein Token-Endpunkt mit
 * Password-Flow. Reicht, damit sich Swagger UI anmelden kann, und stellt Tokens aus, die die App wie die
 * eines echten Providers prüft.
 */
@RestController
@Profile("local")
@RequestMapping("/mock-oidc")
class MockOidcController {

    private final MockOidcKeys keys;

    MockOidcController(MockOidcKeys keys) {
        this.keys = keys;
    }

    @GetMapping(path = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> discovery() {
        String issuer = keys.issuer();
        return Map.of(
                "issuer", issuer,
                "token_endpoint", issuer + "/token",
                "jwks_uri", issuer + "/jwks",
                "grant_types_supported", List.of("password"),
                "response_types_supported", List.of("token"),
                "subject_types_supported", List.of("public"),
                "id_token_signing_alg_values_supported", List.of("RS256"),
                "scopes_supported", List.of("openid", "profile", "email"));
    }

    @GetMapping(path = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return keys.publicJwks();
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> token(@RequestParam("grant_type") String grantType,
            @RequestParam(required = false) String username, @RequestParam(required = false) String password) {
        if (!"password".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "unsupported_grant_type",
                    "error_description", "Der simulierte Provider kennt nur den Password-Flow"));
        }
        Optional<MockUser> user = MockUsers.authenticate(username, password);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_grant",
                    "error_description", "Benutzername oder Passwort falsch (Passwort = Benutzername)"));
        }
        return ResponseEntity.ok(Map.of(
                "access_token", keys.issueAccessToken(user.get()),
                "token_type", "Bearer",
                "expires_in", keys.lifetimeSeconds(),
                "scope", "openid profile email"));
    }
}
