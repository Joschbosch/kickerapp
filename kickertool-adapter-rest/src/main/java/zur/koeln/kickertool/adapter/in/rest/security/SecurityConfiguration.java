package zur.koeln.kickertool.adapter.in.rest.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Die API ist ein OAuth2 Resource Server: Login und Registrierung übernimmt der OIDC-Provider, die API prüft
 * nur die mitgeschickten Access-Tokens (JWT). Der Aussteller kommt aus
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KickertoolSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    AdminRoleJwtConverter adminRoleJwtConverter(KickertoolSecurityProperties properties) {
        return new AdminRoleJwtConverter(properties);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AdminRoleJwtConverter converter) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
