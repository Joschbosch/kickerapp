package zur.koeln.kickertool.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.ForbiddenException;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

class TournamentControllerTest extends ControllerTestBase {

    private final TestData data = TestData.runningTournament();
    private final String id = data.tournament().id().toString();

    // ---------------------------------------------------------------- Authentifizierung

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/tournaments")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }

    // ---------------------------------------------------------------- Lesen

    @Test
    void listsTournaments() throws Exception {
        when(queries.listTournaments()).thenReturn(List.of(new TournamentSummary(data.tournament().id(),
                "Herbstturnier", LocalDate.of(2026, 10, 5), TournamentStatus.RUNNING, 10, 1)));

        mvc.perform(get("/api/tournaments").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Herbstturnier"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[0].participantCount").value(10))
                .andExpect(jsonPath("$[0].roundCount").value(1));
    }

    @Test
    void returnsTournamentWithConfigParticipantsAndCurrentRound() throws Exception {
        when(queries.getTournament(data.tournament().id())).thenReturn(data.view());

        mvc.perform(get("/api/tournaments/{id}", id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Herbstturnier"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.config.tableCount").value(2))
                .andExpect(jsonPath("$.config.pointsWin").value(3))
                .andExpect(jsonPath("$.config.plannedRounds").doesNotExist())
                .andExpect(jsonPath("$.config.randomRounds").value(2))
                .andExpect(jsonPath("$.participants", hasSize(10)))
                .andExpect(jsonPath("$.participants[0].player.displayName").value("Spieler 1"))
                .andExpect(jsonPath("$.participants[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.roundCount").value(1))
                .andExpect(jsonPath("$.currentRound.number").value(1))
                .andExpect(jsonPath("$.currentRound.complete").value(false))
                .andExpect(jsonPath("$.currentRound.matches", hasSize(3)))
                .andExpect(jsonPath("$.currentRound.matches[0].status").value("ON_TABLE"))
                .andExpect(jsonPath("$.currentRound.matches[0].table").value(1))
                .andExpect(jsonPath("$.currentRound.matches[2].status").value("QUEUED"));
    }

    @Test
    void showsDummiesWithoutPlayerInTheQueuedMatch() throws Exception {
        when(queries.getTournament(data.tournament().id())).thenReturn(data.view());

        mvc.perform(get("/api/tournaments/{id}/rounds/current", id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[2].teamA.members[?(@.type=='DUMMY')]", hasSize(1)))
                .andExpect(jsonPath("$.matches[2].teamB.members[?(@.type=='DUMMY')]", hasSize(1)))
                .andExpect(jsonPath("$.matches[2].teamA.members[?(@.type=='PLAYER')].player.displayName", hasSize(1)));
    }

    @Test
    void returnsRankingWithAllStatistics() throws Exception {
        when(queries.getTournament(data.tournament().id())).thenReturn(data.view());

        mvc.perform(get("/api/tournaments/{id}/ranking", id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].points").value(0))
                .andExpect(jsonPath("$[0].matchesPlayed").value(0))
                .andExpect(jsonPath("$[0].goalDifference").value(0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void noCurrentRoundBeforeTheFirstRoundIsNotFound() throws Exception {
        when(queries.getTournament(any())).thenThrow(new NotFoundException("Turnier nicht gefunden"));

        mvc.perform(get("/api/tournaments/{id}/rounds/current", id).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    // ---------------------------------------------------------------- Schreiben

    @Test
    void adminPlansTournamentWithDefaultConfig() throws Exception {
        when(management.plan(any(), any(), any(), any())).thenReturn(data.view());

        mvc.perform(post("/api/tournaments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Herbstturnier", "date": "2026-10-05"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tournaments/" + id)))
                .andExpect(jsonPath("$.id").value(id));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(management).plan(actor.capture(), eq("Herbstturnier"), eq(LocalDate.of(2026, 10, 5)), eq(null));
        assertThat(actor.getValue().admin()).isTrue();
        assertThat(actor.getValue().playerId()).isEqualTo(ANNA.id());
    }

    @Test
    void passesProvidedConfigToPlan() throws Exception {
        when(management.plan(any(), any(), any(), any())).thenReturn(data.view());

        mvc.perform(post("/api/tournaments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cup", "date": "2026-10-05", "config": {
                                  "tableCount": 4, "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                                  "goalLimit": 10, "matchMinutes": 5, "plannedRounds": 6, "randomRounds": 3}}"""))
                .andExpect(status().isCreated());

        verify(management).plan(any(), eq("Cup"), any(), eq(new TournamentConfig(4, 3, 1, 0, 10, 5, 6, 3)));
    }

    @Test
    void normalUsersGetForbiddenFromTheUseCase() throws Exception {
        when(management.plan(any(), any(), any(), any())).thenThrow(new ForbiddenException("Nur Admins dürfen das"));

        mvc.perform(post("/api/tournaments").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cup", "date": "2026-10-05"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Admins dürfen das"));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(management).plan(actor.capture(), any(), any(), any());
        assertThat(actor.getValue().admin()).isFalse();
    }

    @Test
    void invalidRequestBodyIsBadRequest() throws Exception {
        mvc.perform(post("/api/tournaments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  ", "date": "2026-10-05"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));

        mvc.perform(post("/api/tournaments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cup", "date": "2026-10-05", "config": {"tableCount": 0, "pointsWin": 3,
                                 "pointsDraw": 1, "pointsLoss": 0, "goalLimit": 10, "matchMinutes": 5}}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(management);
    }

    @Test
    void negativeNumberOfRandomRoundsIsBadRequest() throws Exception {
        mvc.perform(put("/api/tournaments/{id}/config", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableCount": 3, "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                                 "goalLimit": 10, "matchMinutes": 5, "plannedRounds": null, "randomRounds": -1}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/tournaments/{id}/config", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableCount": 3, "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                                 "goalLimit": 10, "matchMinutes": 5, "plannedRounds": null}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(management);
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/tournaments/{id}", "keine-uuid").with(user())).andExpect(status().isBadRequest());
    }

    @Test
    void updatesConfigDuringTheTournament() throws Exception {
        when(management.updateConfig(any(), any(), any())).thenReturn(data.view());

        mvc.perform(put("/api/tournaments/{id}/config", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableCount": 3, "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                                 "goalLimit": 10, "matchMinutes": 5, "plannedRounds": null, "randomRounds": 4}"""))
                .andExpect(status().isOk());

        verify(management).updateConfig(any(), eq(data.tournament().id()),
                eq(new TournamentConfig(3, 3, 1, 0, 10, 5, null, 4)));
    }

    @Test
    void startsTournamentAndRounds() throws Exception {
        when(management.start(any(), any())).thenReturn(data.view());
        when(management.startNextRound(any(), any())).thenReturn(data.view());
        when(management.finish(any(), any())).thenReturn(data.view());

        mvc.perform(post("/api/tournaments/{id}/start", id).with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/tournaments/{id}/rounds", id).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/rounds/current")));
        mvc.perform(post("/api/tournaments/{id}/finish", id).with(admin())).andExpect(status().isOk());

        verify(management).start(any(), eq(data.tournament().id()));
        verify(management).startNextRound(any(), eq(data.tournament().id()));
        verify(management).finish(any(), eq(data.tournament().id()));
    }

    @Test
    void ruleViolationsAreConflicts() throws Exception {
        when(management.startNextRound(any(), any()))
                .thenThrow(new RuleViolationException("Runde 1 ist noch nicht abgeschlossen"));

        mvc.perform(post("/api/tournaments/{id}/rounds", id).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Runde 1 ist noch nicht abgeschlossen"));
    }

    @Test
    void unknownTournamentIsNotFound() throws Exception {
        when(queries.getTournament(new TournamentId(java.util.UUID.fromString(id))))
                .thenThrow(new NotFoundException("Turnier nicht gefunden: " + id));

        mvc.perform(get("/api/tournaments/{id}", id).with(user())).andExpect(status().isNotFound());
    }
}
