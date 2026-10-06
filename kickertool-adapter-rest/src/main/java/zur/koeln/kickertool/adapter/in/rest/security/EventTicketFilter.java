package zur.koeln.kickertool.adapter.in.rest.security;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Meldet eine Anfrage an den Event-Stream an, wenn sie statt eines Tokens ein gültiges Ticket mitbringt
 * ({@code GET /api/tournaments/{id}/events?ticket=...}). Alle anderen Anfragen laufen unverändert weiter, bei einem
 * ungültigen Ticket bleibt die Anfrage unangemeldet und wird mit 401 abgewiesen.
 */
class EventTicketFilter extends OncePerRequestFilter {

    private static final Pattern STREAM_PATH = Pattern.compile("^/api/tournaments/([0-9a-fA-F-]{36})/events$");
    static final String TICKET_PARAMETER = "ticket";

    private final EventTicketService tickets;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

    EventTicketFilter(EventTicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameter(TICKET_PARAMETER) == null
                || tournamentOf(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Authentication> authentication = tournamentOf(request)
                .flatMap(tournament -> tickets.redeem(request.getParameter(TICKET_PARAMETER), tournament));
        authentication.ifPresent(auth -> {
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(auth);
            contextHolder.setContext(context);
            // Auch für die asynchrone Weiterleitung des Streams merken, wie beim Bearer-Token
            contextRepository.saveContext(context, request, response);
        });
        chain.doFilter(request, response);
    }

    private static Optional<UUID> tournamentOf(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher matcher = STREAM_PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
