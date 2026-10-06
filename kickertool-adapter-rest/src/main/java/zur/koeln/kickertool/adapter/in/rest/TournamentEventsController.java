package zur.koeln.kickertool.adapter.in.rest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.adapter.in.rest.security.EventTicketService;
import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.in.TournamentEventUseCase;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Live-Updates per Server-Sent Events. Die Events enthalten keine Daten, nur den Hinweis, was sich geändert hat.
 * Clients laden danach den neuen Stand über die normalen Endpunkte.
 *
 * <p>Anmelden kann man sich am Stream mit dem Bearer-Token oder, für Browser ohne Header-Möglichkeit, mit einem
 * kurzlebigen Ticket ({@code POST …/events/ticket}). Jedes Event trägt eine Kennung. Verbindet sich ein Client mit der
 * Kennung des letzten empfangenen Events neu ({@code Last-Event-ID}, das sendet der Browser von allein), bekommt er
 * die verpassten Events nachgeliefert oder, wenn das nicht mehr geht, ein {@code RESYNC}.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/events")
@Tag(name = "Live-Updates", description = "Änderungen eines Turniers als Server-Sent Events")
class TournamentEventsController implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TournamentEventsController.class);
    private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long HEARTBEAT_SECONDS = 20;
    /** Nach so vielen Millisekunden verbindet sich der Browser von allein neu. */
    private static final long RECONNECT_MILLIS = 3000;

    private final TournamentEventUseCase events;
    private final EventTicketService tickets;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    TournamentEventsController(TournamentEventUseCase events, EventTicketService tickets) {
        this.events = events;
        this.tickets = tickets;
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @Operation(summary = "Ticket für den Event-Stream holen",
            description = "Der Browser-`EventSource` kann keinen `Authorization`-Header senden. Mit dem Bearer-Token "
                    + "holt die UI hier ein kurzlebiges Ticket und öffnet damit den Stream: "
                    + "`GET …/events?ticket=<ticket>` (die Adresse steht fertig in `streamUrl`). Das Ticket gilt nur für "
                    + "dieses Turnier und nur kurz (`expiresInSeconds`), in dieser Zeit auch mehrfach, damit sich der "
                    + "Browser nach einem Verbindungsabbruch mit derselben Adresse neu verbinden kann. Danach holt die UI "
                    + "ein neues Ticket. Die laufende Verbindung bleibt vom Ablauf unberührt.")
    @PostMapping("/ticket")
    Responses.EventTicket createTicket(@PathVariable UUID tournamentId, Authentication authentication) {
        events.requireTournament(new TournamentId(tournamentId));
        String ticket = tickets.issue(tournamentId, authentication);
        return new Responses.EventTicket(ticket, (int) tickets.ttl().toSeconds(),
                "/api/tournaments/" + tournamentId + "/events?ticket=" + ticket);
    }

    @Operation(summary = "Live-Updates abonnieren",
            description = "Server-Sent Events. Sendet `connected` und danach bei Änderungen die Events "
                    + "`TOURNAMENT_CHANGED`, `PARTICIPANTS_CHANGED`, `ROUND_STARTED`, `MATCHES_CHANGED` und "
                    + "`RANKING_CHANGED` (JSON mit `id`, `type`, `matchId`, `occurredAt`, ohne Nutzdaten). Clients laden "
                    + "danach den neuen Stand über die normalen Endpunkte.\n\n"
                    + "**Anmeldung:** mit dem Bearer-Token im `Authorization`-Header oder mit einem Ticket in `ticket`.\n\n"
                    + "**Nachliefern:** Jedes Event hat eine `id`. Wer sich mit der `id` des letzten empfangenen Events in "
                    + "`Last-Event-ID` (oder, wo kein Header möglich ist, in `lastEventId`) neu verbindet, der Browser "
                    + "macht das bei einer Wiederverbindung desselben `EventSource` von allein, bekommt die verpassten Events "
                    + "nachgeliefert. Geht das nicht mehr (zu lange weg, Server neu gestartet), kommt ein einzelnes "
                    + "`RESYNC`: Dann den Stand komplett neu laden.\n\n"
                    + "In Swagger UI nicht darstellbar, lokal z. B. mit `curl -N -H \"Authorization: Bearer ...\"`.")
    @ApiResponse(responseCode = "200", description = "Dauerhafte Verbindung mit Events",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = Responses.Event.class)))
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID tournamentId,
            @Parameter(in = ParameterIn.HEADER, name = "Last-Event-ID",
                    description = "Kennung des letzten empfangenen Events, für das Nachliefern verpasster Events")
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @Parameter(description = "Ticket statt Bearer-Token, siehe `POST …/events/ticket`")
            @RequestParam(value = "ticket", required = false) String ticket,
            @Parameter(description = "Wie `Last-Event-ID`, für Clients, die keinen Header setzen können: ein neues "
                    + "`EventSource`-Objekt kann das nicht. Der Header hat Vorrang.")
            @RequestParam(value = "lastEventId", required = false) String lastEventIdParameter) throws IOException {
        TournamentId id = new TournamentId(tournamentId);
        String resumeFrom = lastEventId != null && !lastEventId.isBlank() ? lastEventId : lastEventIdParameter;
        events.requireTournament(id);

        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        // Zuerst "connected", dann erst abonnieren: Nachgeliefertes soll danach kommen
        emitter.send(SseEmitter.event().name("connected").data("ok").reconnectTime(RECONNECT_MILLIS));
        EventSubscription subscription = events.subscribe(id, resumeFrom, event -> send(emitter, event));

        Runnable cleanup = () -> {
            subscription.close();
            emitters.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> cleanup.run());
        emitters.add(emitter);
        return emitter;
    }

    private void send(SseEmitter emitter, TournamentEvent event) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.type().name())
                    .data(RestMapper.event(event), MediaType.APPLICATION_JSON);
            if (event.id() != null) {
                builder.id(event.id());
            }
            emitter.send(builder);
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE-Client nicht mehr erreichbar: {}", e.toString());
            emitter.complete();
        }
    }

    private void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitter.complete();
            }
        }
    }

    /**
     * Beim Herunterfahren zuerst alle Streams beenden. Sonst wartet das Graceful Shutdown des Servers auf die
     * offenen Verbindungen, und der Neustart hängt, solange Handys verbunden sind. Clients verbinden sich neu.
     */
    @EventListener(ContextClosedEvent.class)
    void closeStreamsOnShutdown() {
        emitters.forEach(SseEmitter::complete);
    }

    @Override
    public void destroy() {
        heartbeat.shutdownNow();
    }
}
