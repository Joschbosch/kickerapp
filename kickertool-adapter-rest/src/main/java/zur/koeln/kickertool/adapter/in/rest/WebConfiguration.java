package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import zur.koeln.kickertool.adapter.in.rest.security.ActorArgumentResolver;
import zur.koeln.kickertool.adapter.in.rest.security.KickertoolSecurityProperties;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final PlayerUseCase players;
    private final KickertoolSecurityProperties properties;

    public WebConfiguration(PlayerUseCase players, KickertoolSecurityProperties properties) {
        this.players = players;
        this.properties = properties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver(players, properties));
    }
}
