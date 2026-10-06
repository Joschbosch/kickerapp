package zur.koeln.kickertool.adapter.in.rest.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen für die Live-Updates.
 *
 * @param ticketTtl So lange gilt ein Stream-Ticket. Es darf in dieser Zeit mehrfach verwendet werden, damit sich ein
 *                  Browser nach einem kurzen Verbindungsabbruch von allein wieder verbinden kann. Umgebungsvariable:
 *                  {@code KICKERTOOL_EVENTS_TICKET_TTL} (z. B. {@code 2m} oder {@code 30s})
 */
@ConfigurationProperties(prefix = "kickertool.events")
public record KickertoolEventsProperties(@DefaultValue("2m") Duration ticketTtl) {
}
