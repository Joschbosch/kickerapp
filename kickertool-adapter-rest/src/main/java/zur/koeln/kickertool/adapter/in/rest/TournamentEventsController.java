package zur.koeln.kickertool.adapter.in.rest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.in.TournamentEventUseCase;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Live-Updates per Server-Sent Events. Die Events enthalten keine Daten, nur den Hinweis, was sich geändert hat.
 * Clients laden danach den neuen Stand über die normalen Endpunkte. Der Endpunkt ist wie alle anderen über das
 * Bearer-Token abgesichert, Clients müssen den {@code Authorization}-Header setzen können.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/events")
@Tag(name = "Live-Updates", description = "Änderungen eines Turniers als Server-Sent Events")
class TournamentEventsController implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TournamentEventsController.class);
    private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long HEARTBEAT_SECONDS = 20;

    private final TournamentEventUseCase events;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    TournamentEventsController(TournamentEventUseCase events) {
        this.events = events;
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @Operation(summary = "Live-Updates abonnieren",
            description = "Server-Sent Events. Sendet `connected` und danach bei Änderungen die Events "
                    + "`TOURNAMENT_CHANGED`, `PARTICIPANTS_CHANGED`, `ROUND_STARTED`, `MATCHES_CHANGED` und "
                    + "`RANKING_CHANGED` (JSON mit `type`, `matchId`, `occurredAt`, ohne Nutzdaten). Clients laden "
                    + "danach den neuen Stand über die normalen Endpunkte. Braucht den Authorization-Header, in "
                    + "Swagger UI nicht darstellbar, lokal z. B. mit `curl -N -H \"Authorization: Bearer ...\"`.")
    @ApiResponse(responseCode = "200", description = "Dauerhafte Verbindung mit Events",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(implementation = Responses.Event.class)))
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID tournamentId) throws IOException {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        EventSubscription subscription = events.subscribe(new TournamentId(tournamentId), event -> send(emitter, event));

        Runnable cleanup = () -> {
            subscription.close();
            emitters.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> cleanup.run());
        emitters.add(emitter);

        emitter.send(SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    private void send(SseEmitter emitter, TournamentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(RestMapper.event(event), MediaType.APPLICATION_JSON));
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
