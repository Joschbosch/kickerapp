package zur.koeln.kickertool.local;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Nur mit dem Spring-Profil {@code local}: ersetzt den OIDC-Provider durch eine Simulation in der App selbst.
 * Die App prüft die Tokens des simulierten Providers mit dessen Schlüssel statt über einen Aussteller im Netz.
 * <b>Niemals in einer echten Umgebung aktivieren</b>, jeder kann sich als Admin anmelden.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalConfiguration.class);

    @Bean
    MockOidcKeys mockOidcKeys(@Value("${kickertool.local.issuer:http://localhost:${server.port:8080}/mock-oidc}")
            String issuer) {
        log.warn("""

                ============================================================
                 LOKALER TESTMODUS: simulierter OIDC-Provider ist aktiv.
                 Jeder kann sich als Admin anmelden. Nur zum Ausprobieren!
                 Aussteller: {}
                ============================================================""", issuer);
        return new MockOidcKeys(issuer);
    }

    /** Prüft die Tokens mit dem Schlüssel des simulierten Providers. */
    @Bean
    JwtDecoder jwtDecoder(MockOidcKeys keys) {
        return NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
    }

    /**
     * Die Endpunkte des simulierten Providers sind offen, sie stellen ja erst die Tokens aus. Eine UI auf einer anderen
     * lokalen Adresse (z. B. Vite auf :5173) holt hier direkt ihr Token, deshalb gilt für diese Pfade CORS für alle
     * Adressen. Das ist nur im lokalen Testmodus aktiv.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain mockOidcSecurity(HttpSecurity http) {
        CorsConfiguration anyOrigin = new CorsConfiguration();
        anyOrigin.setAllowedOriginPatterns(List.of("*"));
        anyOrigin.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        anyOrigin.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/mock-oidc/**", anyOrigin);

        http.securityMatcher("/mock-oidc/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(source))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        return http.build();
    }
}
