package zur.koeln.kickertool.adapter.in.rest.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Welche Web-Oberflächen die API aus dem Browser aufrufen dürfen (CORS). Standard: keine, die API ist dann nur von
 * derselben Adresse aus erreichbar. Eine UI auf einer anderen Adresse (z. B. {@code http://localhost:5173}) muss
 * hier eingetragen werden.
 *
 * @param allowedOrigins erlaubte Herkunftsadressen, ohne Pfad und ohne abschließenden Schrägstrich. Muster wie
 *                       {@code https://*.example.com} sind erlaubt. Umgebungsvariable:
 *                       {@code KICKERTOOL_CORS_ALLOWED_ORIGINS} (kommagetrennt)
 */
@ConfigurationProperties(prefix = "kickertool.cors")
public record KickertoolCorsProperties(@DefaultValue List<String> allowedOrigins) {
}
