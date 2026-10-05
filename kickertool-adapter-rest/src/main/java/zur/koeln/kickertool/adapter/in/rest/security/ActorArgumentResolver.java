package zur.koeln.kickertool.adapter.in.rest.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.domain.player.Player;

/**
 * Löst Controller-Parameter vom Typ {@link Actor} aus dem Token des Aufrufers auf. Beim ersten Aufruf wird der
 * Spieler dabei automatisch angelegt (Registrierung läuft im OIDC-Provider, hier wird nur das Profil geführt).
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final PlayerUseCase players;
    private final KickertoolSecurityProperties properties;

    public ActorArgumentResolver(PlayerUseCase players, KickertoolSecurityProperties properties) {
        this.players = players;
        this.properties = properties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("Kein Token vorhanden");
        }
        Jwt jwt = token.getToken();
        Player player = players.provision(jwt.getSubject(), displayName(jwt));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> AdminRoleJwtConverter.ADMIN_AUTHORITY.equals(a.getAuthority()));
        return new Actor(player.id(), admin);
    }

    private String displayName(Jwt jwt) {
        for (String claim : properties.displayNameClaims()) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return jwt.getSubject();
    }
}
