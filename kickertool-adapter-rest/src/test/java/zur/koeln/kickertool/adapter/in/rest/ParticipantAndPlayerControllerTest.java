package zur.koeln.kickertool.adapter.in.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import zur.koeln.kickertool.application.ForbiddenException;
import zur.koeln.kickertool.application.view.PlayerStatisticsView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.statistics.PlayerStatistics;
import zur.koeln.kickertool.domain.tournament.TournamentId;

class ParticipantAndPlayerControllerTest extends ControllerTestBase {

    private final TestData data = TestData.runningTournament();
    private final TournamentId tournamentId = data.tournament().id();
    private final String base = "/api/tournaments/" + tournamentId + "/participants";

    // ---------------------------------------------------------------- Teilnahme

    @Test
    void registersTheCallerWithoutBody() throws Exception {
        when(participation.register(any(), any(), any())).thenReturn(data.view());

        mvc.perform(post(base).with(user()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants", hasSize(10)));

        verify(participation).register(any(), eq(tournamentId), eq(ANNA.id()));
    }

    @Test
    void adminRegistersAnotherPlayer() throws Exception {
        UUID other = UUID.randomUUID();
        when(participation.register(any(), any(), any())).thenReturn(data.view());

        mvc.perform(post(base).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": \"" + other + "\"}"))
                .andExpect(status().isCreated());

        verify(participation).register(any(), eq(tournamentId), eq(new PlayerId(other)));
    }

    @Test
    void unregistersWithMeAlias() throws Exception {
        when(participation.unregister(any(), any(), any())).thenReturn(data.view());

        mvc.perform(delete(base + "/me").with(user())).andExpect(status().isOk());

        verify(participation).unregister(any(), eq(tournamentId), eq(ANNA.id()));
    }

    @Test
    void unregisteringAfterStartIsAConflict() throws Exception {
        when(participation.unregister(any(), any(), any()))
                .thenThrow(new RuleViolationException("Abmelden ist nur vor dem Start möglich"));

        mvc.perform(delete(base + "/me").with(user())).andExpect(status().isConflict());
    }

    @Test
    void changesStatusToPausedActiveAndWithdrawn() throws Exception {
        when(participation.pause(any(), any(), any())).thenReturn(data.view());
        when(participation.resume(any(), any(), any())).thenReturn(data.view());
        when(participation.withdraw(any(), any(), any())).thenReturn(data.view());

        for (String status : List.of("PAUSED", "ACTIVE", "WITHDRAWN")) {
            mvc.perform(put(base + "/me/status").with(user()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"" + status + "\"}"))
                    .andExpect(status().isOk());
        }

        verify(participation).pause(any(), eq(tournamentId), eq(ANNA.id()));
        verify(participation).resume(any(), eq(tournamentId), eq(ANNA.id()));
        verify(participation).withdraw(any(), eq(tournamentId), eq(ANNA.id()));
    }

    @Test
    void changingAnotherPlayersStatusIsForbiddenForNormalUsers() throws Exception {
        when(participation.pause(any(), any(), any())).thenThrow(new ForbiddenException("nicht erlaubt"));

        mvc.perform(put(base + "/" + UUID.randomUUID() + "/status").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PAUSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownStatusValueIsBadRequest() throws Exception {
        mvc.perform(put(base + "/me/status").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SCHLAFEN\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Spieler

    @Test
    void meReturnsProfileAndAdminFlag() throws Exception {
        mvc.perform(get("/api/me").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANNA.id().toString()))
                .andExpect(jsonPath("$.displayName").value("Anna"))
                .andExpect(jsonPath("$.admin").value(false));

        mvc.perform(get("/api/me").with(admin()))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void provisionsPlayerFromTokenClaims() throws Exception {
        mvc.perform(get("/api/me").with(user())).andExpect(status().isOk());

        verify(players).provision("sub-anna", "Anna");
    }

    @Test
    void listsPlayersForAdmins() throws Exception {
        Player ben = new Player(PlayerId.random(), "sub-ben", "Ben");
        when(players.listPlayers(any())).thenReturn(List.of(ANNA, ben));

        mvc.perform(get("/api/players").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].displayName").value("Ben"));
    }

    @Test
    void anyPlayerCanReadTheStatisticOfAnyPlayer() throws Exception {
        Player ben = new Player(PlayerId.random(), "sub-ben", "Ben");
        when(statistics.statistics(ben.id())).thenReturn(new PlayerStatisticsView(ben,
                PlayerStatistics.of(ben.id(), List.of(data.tournament()))));
        PlayerId inTournament = data.players().get(0).id();
        when(statistics.statistics(inTournament)).thenReturn(new PlayerStatisticsView(data.players().get(0),
                PlayerStatistics.of(inTournament, List.of(data.tournament()))));

        mvc.perform(get("/api/players/" + ben.id() + "/statistics").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.displayName").value("Ben"))
                .andExpect(jsonPath("$.totals.tournamentCount").value(0))
                .andExpect(jsonPath("$.tournaments", hasSize(0)));

        mvc.perform(get("/api/players/" + inTournament + "/statistics").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.displayName").value("Spieler 1"))
                .andExpect(jsonPath("$.totals.tournamentCount").value(1))
                .andExpect(jsonPath("$.totals.finishedTournamentCount").value(0))
                .andExpect(jsonPath("$.tournaments", hasSize(1)))
                .andExpect(jsonPath("$.tournaments[0].name").value("Herbstturnier"))
                .andExpect(jsonPath("$.tournaments[0].tournamentStatus").value("RUNNING"))
                .andExpect(jsonPath("$.tournaments[0].participantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.tournaments[0].participantCount").value(10))
                .andExpect(jsonPath("$.tournaments[0].rank").value(1));
    }

    @Test
    void meStandsForTheCallerInTheStatisticPath() throws Exception {
        when(statistics.statistics(ANNA.id())).thenReturn(new PlayerStatisticsView(ANNA,
                PlayerStatistics.of(ANNA.id(), List.of())));

        mvc.perform(get("/api/players/me/statistics").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.id").value(ANNA.id().toString()))
                .andExpect(jsonPath("$.player.displayName").value("Anna"));
    }

    @Test
    void statisticOfAnUnknownPlayerIsNotFound() throws Exception {
        PlayerId unknown = PlayerId.random();
        when(statistics.statistics(unknown)).thenThrow(new NotFoundException("Spieler nicht gefunden"));

        mvc.perform(get("/api/players/" + unknown + "/statistics").with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void statisticRequiresLogin() throws Exception {
        mvc.perform(get("/api/players/" + UUID.randomUUID() + "/statistics")).andExpect(status().isUnauthorized());
    }

    @Test
    void statisticWithAMalformedPlayerIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/players/kein-uuid/statistics").with(user())).andExpect(status().isBadRequest());
    }

    @Test
    void listingPlayersAsNormalUserIsForbidden() throws Exception {
        when(players.listPlayers(any())).thenThrow(new ForbiddenException("Nur Admins dürfen das"));

        mvc.perform(get("/api/players").with(user())).andExpect(status().isForbidden());
    }
}
