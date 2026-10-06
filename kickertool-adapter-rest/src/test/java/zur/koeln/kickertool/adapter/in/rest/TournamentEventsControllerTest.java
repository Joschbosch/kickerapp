package zur.koeln.kickertool.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.jayway.jsonpath.JsonPath;

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

    private final UUID tournament = UUID.randomUUID();
    private final TournamentId tournamentId = new TournamentId(tournament);
    private final String streamPath = "/api/tournaments/{id}/events";

    private AtomicReference<Consumer<TournamentEvent>> captureListener() {
        AtomicReference<Consumer<TournamentEvent>> listener = new AtomicReference<>();
        when(events.subscribe(any(), any(), any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(2));
            return (EventSubscription) () -> { };
        });
        return listener;
    }

    private String issueTicket(UUID forTournament) throws Exception {
        MvcResult result = mvc.perform(post(streamPath + "/ticket", forTournament).with(user()))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.ticket");
    }

    // ---------------------------------------------------------------- Stream

    @Test
    void streamsEventsAsServerSentEventsWithIds() throws Exception {
        AtomicReference<Consumer<TournamentEvent>> listener = captureListener();

        MvcResult result = mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(request().asyncStarted())
                .andReturn();
        MatchId matchId = MatchId.random();
        listener.get().accept(new TournamentEvent(tournamentId, TournamentEventType.MATCHES_CHANGED, matchId,
                Instant.parse("2026-10-05T10:00:00Z"), "abc-17"));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:connected").contains("retry:3000");
        assertThat(body).contains("id:abc-17").contains("event:MATCHES_CHANGED");
        assertThat(body).contains("\"type\":\"MATCHES_CHANGED\"").contains("\"id\":\"abc-17\"")
                .contains("\"matchId\":\"" + matchId + "\"");
    }

    @Test
    void sendsConnectedBeforeAnyReplayedEvent() throws Exception {
        AtomicReference<Consumer<TournamentEvent>> listener = captureListener();

        MvcResult result = mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(request().asyncStarted()).andReturn();
        listener.get().accept(new TournamentEvent(tournamentId, TournamentEventType.RANKING_CHANGED, null,
                Instant.parse("2026-10-05T10:00:00Z"), "abc-1"));

        String body = result.getResponse().getContentAsString();
        assertThat(body.indexOf("event:connected")).isLessThan(body.indexOf("event:RANKING_CHANGED"));
    }

    @Test
    void passesTheLastEventIdFromTheBrowserOn() throws Exception {
        captureListener();

        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).header("Last-Event-ID", "abc-7")
                        .with(user()))
                .andExpect(request().asyncStarted());

        verify(events).subscribe(eq(tournamentId), eq("abc-7"), any());
    }

    @Test
    void acceptsTheLastEventIdAsQueryParameterForClientsWithoutHeaders() throws Exception {
        captureListener();

        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).param("lastEventId", "abc-9")
                        .with(user()))
                .andExpect(request().asyncStarted());

        verify(events).subscribe(eq(tournamentId), eq("abc-9"), any());
    }

    @Test
    void theHeaderWinsOverTheQueryParameter() throws Exception {
        captureListener();

        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).param("lastEventId", "abc-9")
                        .header("Last-Event-ID", "abc-12").with(user()))
                .andExpect(request().asyncStarted());

        verify(events).subscribe(eq(tournamentId), eq("abc-12"), any());
    }

    @Test
    void aFreshConnectionHasNoLastEventId() throws Exception {
        captureListener();

        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(request().asyncStarted());

        verify(events).subscribe(eq(tournamentId), eq(null), any());
    }

    @Test
    void unknownTournamentIsNotFound() throws Exception {
        doThrow(new NotFoundException("Turnier nicht gefunden")).when(events).requireTournament(any());

        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(streamPath, tournament).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- Ticket

    @Test
    void issuesATicketForTheLoggedInPlayer() throws Exception {
        mvc.perform(post(streamPath + "/ticket", tournament).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andExpect(jsonPath("$.streamUrl", startsWith("/api/tournaments/" + tournament + "/events?ticket=")));
    }

    @Test
    void ticketsAreOnlyIssuedToLoggedInPlayersAndForExistingTournaments() throws Exception {
        mvc.perform(post(streamPath + "/ticket", tournament)).andExpect(status().isUnauthorized());

        doThrow(new NotFoundException("Turnier nicht gefunden")).when(events).requireTournament(any());
        mvc.perform(post(streamPath + "/ticket", tournament).with(user())).andExpect(status().isNotFound());
    }

    @Test
    void opensTheStreamWithATicketAndWithoutAnAuthorizationHeader() throws Exception {
        captureListener();
        String ticket = issueTicket(tournament);

        mvc.perform(get(streamPath, tournament).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    @Test
    void aTicketCanBeUsedAgainForAReconnect() throws Exception {
        captureListener();
        String ticket = issueTicket(tournament);

        for (int i = 0; i < 3; i++) {
            mvc.perform(get(streamPath, tournament).param("ticket", ticket).header("Last-Event-ID", "abc-" + i)
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted());
        }
    }

    @Test
    void aTicketOnlyWorksForItsOwnTournament() throws Exception {
        String ticket = issueTicket(tournament);

        mvc.perform(get(streamPath, UUID.randomUUID()).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTicketsAreRejected() throws Exception {
        mvc.perform(get(streamPath, tournament).param("ticket", "erfunden").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(streamPath, tournament).param("ticket", "").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTicketDoesNotOpenAnyOtherEndpoint() throws Exception {
        String ticket = issueTicket(tournament);

        mvc.perform(get("/api/tournaments").param("ticket", ticket)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tournaments/{id}", tournament).param("ticket", ticket))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(streamPath + "/ticket", tournament).param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }
}
