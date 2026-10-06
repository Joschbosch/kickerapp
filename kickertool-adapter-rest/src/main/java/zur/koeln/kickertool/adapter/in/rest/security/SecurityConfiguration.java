package zur.koeln.kickertool.adapter.in.rest.security;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Die API ist ein OAuth2 Resource Server: Login und Registrierung übernimmt der OIDC-Provider, die API prüft
 * nur die mitgeschickten Access-Tokens (JWT). Der Aussteller kommt aus
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({KickertoolSecurityProperties.class, KickertoolCorsProperties.class,
        KickertoolEventsProperties.class})
public class SecurityConfiguration {

    @Bean
    AdminRoleJwtConverter adminRoleJwtConverter(KickertoolSecurityProperties properties) {
        return new AdminRoleJwtConverter(properties);
    }

    /** Tickets für den Event-Stream, den der Browser ohne Authorization-Header öffnen muss. */
    @Bean
    EventTicketService eventTicketService(KickertoolEventsProperties properties) {
        return new EventTicketService(Clock.systemUTC(), properties.ticketTtl());
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AdminRoleJwtConverter converter,
            EventTicketService eventTickets) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // API-Dokumentation und Swagger UI sind öffentlich, die Aufrufe der API selbst nicht
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                // Der Event-Stream darf statt eines Tokens ein Ticket in der Adresse tragen
                .addFilterBefore(new EventTicketFilter(eventTickets), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS für Web-Oberflächen auf einer anderen Adresse. Gilt für die ganze API, auch für Preflight-Anfragen, die
     * kein Token mitschicken. Cookies werden nicht freigegeben, die API arbeitet mit dem Bearer-Token.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(KickertoolCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
