package zur.koeln.kickertool.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

class TournamentEventsControllerTest extends ControllerTestBase {

    private final TournamentId tournamentId = TournamentId.random();

    @Test
    void streamsEventsAsServerSentEvents() throws Exception {
        AtomicReference<Consumer<TournamentEvent>> listener = new AtomicReference<>();
        when(events.subscribe(any(), any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            return (EventSubscription) () -> { };
        });

        MvcResult result = mvc.perform(get("/api/tournaments/{id}/events", tournamentId)
                        .accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(request().asyncStarted())
                .andReturn();
        MatchId matchId = MatchId.random();
        listener.get().accept(new TournamentEvent(tournamentId, TournamentEventType.MATCHES_CHANGED, matchId,
                Instant.parse("2026-10-05T10:00:00Z")));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:connected");
        assertThat(body).contains("event:MATCHES_CHANGED");
        assertThat(body).contains("\"type\":\"MATCHES_CHANGED\"").contains("\"matchId\":\"" + matchId + "\"");
        verify(events).subscribe(any(), any());
    }

    @Test
    void unknownTournamentIsNotFound() throws Exception {
        when(events.subscribe(any(), any())).thenThrow(new NotFoundException("Turnier nicht gefunden"));

        mvc.perform(get("/api/tournaments/{id}/events", tournamentId).accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/tournaments/{id}/events", tournamentId).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
