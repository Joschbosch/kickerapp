package zur.koeln.kickertool.adapter.in.rest.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminRoleJwtConverterTest {

    private static final KickertoolSecurityProperties KEYCLOAK_DEFAULTS =
            new KickertoolSecurityProperties("realm_access.roles", "kicker-admin", List.of("name"));

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-1").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claims(c -> c.putAll(claims)).build();
    }

    private static List<String> authorities(KickertoolSecurityProperties properties, Map<String, Object> claims) {
        return new AdminRoleJwtConverter(properties).convert(jwt(claims)).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void keycloakRealmRoleMakesAnAdmin() {
        assertThat(authorities(KEYCLOAK_DEFAULTS,
                Map.of("realm_access", Map.of("roles", List.of("offline_access", "kicker-admin")))))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void withoutTheRoleThereAreNoAuthorities() {
        assertThat(authorities(KEYCLOAK_DEFAULTS,
                Map.of("realm_access", Map.of("roles", List.of("offline_access"))))).isEmpty();
    }

    @Test
    void missingClaimMeansNoAdmin() {
        assertThat(authorities(KEYCLOAK_DEFAULTS, Map.of())).isEmpty();
        assertThat(authorities(KEYCLOAK_DEFAULTS, Map.of("realm_access", "kaputt"))).isEmpty();
    }

    @Test
    void supportsClientRolesViaConfiguredPath() {
        KickertoolSecurityProperties properties =
                new KickertoolSecurityProperties("resource_access.kickertool-app.roles", "organizer", List.of("name"));

        assertThat(authorities(properties,
                Map.of("resource_access", Map.of("kickertool-app", Map.of("roles", List.of("organizer"))))))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void supportsTopLevelClaimsAsListOrSpaceSeparatedString() {
        KickertoolSecurityProperties groups = new KickertoolSecurityProperties("groups", "kicker-admin", List.of("name"));
        assertThat(authorities(groups, Map.of("groups", List.of("kicker-admin")))).containsExactly("ROLE_ADMIN");

        KickertoolSecurityProperties scope = new KickertoolSecurityProperties("scope", "kicker:admin", List.of("name"));
        assertThat(authorities(scope, Map.of("scope", "openid kicker:admin"))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void claimNamesContainingDotsAreResolvedAsWholeName() {
        KickertoolSecurityProperties properties = new KickertoolSecurityProperties(
                "https://kicker.example/roles", "kicker-admin", List.of("name"));

        assertThat(authorities(properties, Map.of("https://kicker.example/roles", List.of("kicker-admin"))))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void principalNameIsTheSubject() {
        assertThat(new AdminRoleJwtConverter(KEYCLOAK_DEFAULTS).convert(jwt(Map.of())).getName()).isEqualTo("user-1");
    }
}
