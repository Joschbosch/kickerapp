package zur.koeln.kickertool.adapter.in.rest.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen zur Auswertung des OIDC-Tokens. Die Anwendung ist unabhängig vom konkreten Provider, nur diese
 * Claim-Namen müssen zum Provider passen. Die Defaults passen zu Keycloak.
 *
 * @param adminClaim        Pfad zum Claim mit den Rollen, verschachtelt mit Punkten
 *                          (Keycloak: {@code realm_access.roles}, oder {@code resource_access.<client>.roles})
 * @param adminRole         Rolle, die einen Spieler zum Admin macht
 * @param displayNameClaims Claims für den Anzeigenamen, der erste vorhandene gewinnt
 */
@ConfigurationProperties(prefix = "kickertool.security")
public record KickertoolSecurityProperties(
        @DefaultValue("realm_access.roles") String adminClaim,
        @DefaultValue("kicker-admin") String adminRole,
        @DefaultValue({"name", "preferred_username", "email"}) List<String> displayNameClaims) {
}
