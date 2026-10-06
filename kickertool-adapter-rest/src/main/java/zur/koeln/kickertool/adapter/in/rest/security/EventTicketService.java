package zur.koeln.kickertool.adapter.in.rest.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.Authentication;

/**
 * Kurzlebige Tickets, mit denen ein Browser den Event-Stream öffnen kann. Der Browser-{@code EventSource} kann keinen
 * {@code Authorization}-Header senden. Stattdessen holt die UI mit ihrem Token ein Ticket und hängt es an die Adresse
 * des Streams.
 *
 * <p>Ein Ticket gilt nur für ein Turnier und nur kurz, und es steht stellvertretend für die Anmeldung dessen, der es
 * geholt hat. Es darf in dieser Zeit mehrfach verwendet werden, damit der Browser sich nach einem Verbindungsabbruch mit
 * derselben Adresse neu verbinden kann. Weil die Events keine Daten enthalten, wäre ein abgeflossenes Ticket harmlos.
 * Es sollte trotzdem nicht in Protokolle geraten. Die Tickets liegen im Speicher dieser Instanz.
 */
public class EventTicketService {

    private static final int TICKET_BYTES = 32;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;

    public EventTicketService(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Stellt ein Ticket für den angemeldeten Aufrufer und das Turnier aus. */
    public String issue(UUID tournamentId, Authentication authentication) {
        purgeExpired();
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(tournamentId, authentication, clock.instant().plus(ttl)));
        return ticket;
    }

    /** Die Anmeldung hinter dem Ticket, wenn es gültig ist, nicht abgelaufen und für dieses Turnier ausgestellt wurde. */
    public Optional<Authentication> redeem(String ticket, UUID tournamentId) {
        Ticket found = ticket == null ? null : tickets.get(ticket);
        if (found == null || !found.tournamentId().equals(tournamentId)) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(found.expiresAt())) {
            tickets.remove(ticket);
            return Optional.empty();
        }
        return Optional.of(found.authentication());
    }

    int size() {
        return tickets.size();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tickets.values().removeIf(ticket -> !now.isBefore(ticket.expiresAt()));
    }

    private record Ticket(UUID tournamentId, Authentication authentication, Instant expiresAt) {
    }
}
