package zur.koeln.kickertool.adapter.in.rest.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Macht aus dem Token eine Authentifizierung. Enthält der konfigurierte Claim die Admin-Rolle, bekommt der
 * Nutzer die Authority {@value #ADMIN_AUTHORITY}.
 */
public class AdminRoleJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final KickertoolSecurityProperties properties;

    public AdminRoleJwtConverter(KickertoolSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        boolean admin = ClaimPaths.values(jwt.getClaims(), properties.adminClaim()).contains(properties.adminRole());
        List<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority(ADMIN_AUTHORITY))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
