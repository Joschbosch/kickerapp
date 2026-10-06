package zur.koeln.kickertool.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.ForbiddenException;
import zur.koeln.kickertool.domain.NotPermittedException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;

class MatchControllerTest extends ControllerTestBase {

    private final TestData data = TestData.runningTournament();
    private final String tournamentId = data.tournament().id().toString();
    private final Match firstMatch = data.tournament().rounds().get(0).matches().get(0);
    private final String matchId = firstMatch.id().toString();
    private final String base = "/api/tournaments/{t}/matches";

    @BeforeEach
    void stubTournament() {
        when(queries.getTournament(data.tournament().id())).thenReturn(data.view());
    }

    /** Lässt den Aufrufer ein Spieler aus Team A des ersten Matches sein. */
    private Player playerOfFirstMatch() {
        var id = firstMatch.teamA().realPlayers().get(0);
        Player player = data.players().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
        when(players.provision(any(), any())).thenReturn(player);
        return player;
    }

    @Test
    void returnsASingleMatch() throws Exception {
        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.status").value("ON_TABLE"))
                .andExpect(jsonPath("$.table").value(1))
                .andExpect(jsonPath("$.teamA.members", hasSize(2)))
                .andExpect(jsonPath("$.teamB.members", hasSize(2)))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    // ---------------------------------------------------------------- Berechtigungen für den Aufrufer

    private static final String SEITE = "$.mySide";

    @Test
    void flagsTellAPlayerAtTheTableThatHeMayEnterAResult() throws Exception {
        playerOfFirstMatch();

        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SEITE).value("A"))
                .andExpect(jsonPath("$.permissions.canEnterResult").value(true))
                .andExpect(jsonPath("$.permissions.canConfirm").value(false))
                .andExpect(jsonPath("$.permissions.canReject").value(false))
                .andExpect(jsonPath("$.permissions.canDecide").value(false));
    }

    @Test
    void flagsAreAllFalseForSomeoneWhoIsNotInTheMatch() throws Exception {
        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                .andExpect(jsonPath(SEITE).doesNotExist())
                .andExpect(jsonPath("$.permissions.canEnterResult").value(false))
                .andExpect(jsonPath("$.permissions.canConfirm").value(false))
                .andExpect(jsonPath("$.permissions.canDecide").value(false));
    }

    @Test
    void onlyTheOpposingTeamMayConfirmOrRejectAfterAResultWasEntered() throws Exception {
        PlayerId enteringPlayer = firstMatch.teamA().actingPlayers().get(0);
        PlayerId partner = firstMatch.teamA().actingPlayers().size() > 1 ? firstMatch.teamA().actingPlayers().get(1) : null;
        PlayerId opponent = firstMatch.teamB().actingPlayers().get(0);
        data.tournament().submitResult(firstMatch.id(), enteringPlayer, new MatchResult(10, 6),
                new NearestRankStandInSuggester());

        Player enteringUser = playerNamed(enteringPlayer);
        when(players.provision(any(), any())).thenReturn(enteringUser);
        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                .andExpect(jsonPath("$.permissions.canEnterResult").value(false))
                .andExpect(jsonPath("$.permissions.canConfirm").value(false));

        if (partner != null) {
            when(players.provision(any(), any())).thenReturn(playerNamed(partner));
            mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                    // Partner des Eintragenden darf nicht bestätigen
                    .andExpect(jsonPath("$.permissions.canConfirm").value(false));
        }

        when(players.provision(any(), any())).thenReturn(playerNamed(opponent));
        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(user()))
                .andExpect(jsonPath(SEITE).value("B"))
                .andExpect(jsonPath("$.permissions.canConfirm").value(true))
                .andExpect(jsonPath("$.permissions.canReject").value(true));
    }

    @Test
    void adminMayDecideRunningMatchesButNotWaitingOnes() throws Exception {
        Match waiting = data.tournament().rounds().get(0).matches().get(2);

        mvc.perform(get(base + "/{m}", tournamentId, matchId).with(admin()))
                .andExpect(jsonPath("$.permissions.canDecide").value(true))
                .andExpect(jsonPath("$.permissions.canEnterResult").value(false));
        mvc.perform(get(base + "/{m}", tournamentId, waiting.id()).with(admin()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.permissions.canDecide").value(false));
    }

    @Test
    void flagsAppearInTheTournamentViewToo() throws Exception {
        playerOfFirstMatch();

        mvc.perform(get("/api/tournaments/{id}", tournamentId).with(user()))
                .andExpect(jsonPath("$.currentRound.matches[0].permissions.canEnterResult").value(true))
                .andExpect(jsonPath("$.currentRound.matches[1].permissions.canEnterResult").value(false));
    }

    private Player playerNamed(PlayerId id) {
        return data.players().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void unknownMatchIsNotFound() throws Exception {
        mvc.perform(get(base + "/{m}", tournamentId, java.util.UUID.randomUUID()).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyTheMatchesOfTheCaller() throws Exception {
        playerOfFirstMatch();

        mvc.perform(get(base + "/mine", tournamentId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(matchId));
    }

    @Test
    void callerWithoutMatchGetsEmptyList() throws Exception {
        // ANNA ist nicht Teil des Turniers
        mvc.perform(get(base + "/mine", tournamentId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void proposesResultAsPlayerOfTheMatch() throws Exception {
        Player caller = playerOfFirstMatch();
        when(results.submitResult(any(), any(), any(), any())).thenReturn(data.view());

        mvc.perform(post(base + "/{m}/result-proposal", tournamentId, matchId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": 10, "goalsB": 7}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(results).submitResult(actor.capture(), eq(data.tournament().id()), eq(firstMatch.id()),
                eq(new MatchResult(10, 7)));
        assertThat(actor.getValue().playerId()).isEqualTo(caller.id());
        assertThat(actor.getValue().admin()).isFalse();
    }

    @Test
    void rejectsNegativeOrMissingGoals() throws Exception {
        mvc.perform(post(base + "/{m}/result-proposal", tournamentId, matchId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": -1, "goalsB": 7}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/{m}/result-proposal", tournamentId, matchId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": 3}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(results);
    }

    @Test
    void invalidResultFromTheDomainIsBadRequest() throws Exception {
        when(results.submitResult(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Kein Team darf mehr als 10 Tore haben"));

        mvc.perform(post(base + "/{m}/result-proposal", tournamentId, matchId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": 11, "goalsB": 7}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Kein Team darf mehr als 10 Tore haben"));
    }

    @Test
    void playersOutsideTheMatchAreForbidden() throws Exception {
        when(results.confirmResult(any(), any(), any()))
                .thenThrow(new NotPermittedException("Der Spieler gehört nicht zu diesem Match"));

        mvc.perform(post(base + "/{m}/result-proposal/confirmation", tournamentId, matchId).with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmsAndRejectsResults() throws Exception {
        when(results.confirmResult(any(), any(), any())).thenReturn(data.view());
        when(results.rejectResult(any(), any(), any())).thenReturn(data.view());

        mvc.perform(post(base + "/{m}/result-proposal/confirmation", tournamentId, matchId).with(user()))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/{m}/result-proposal/rejection", tournamentId, matchId).with(user()))
                .andExpect(status().isOk());

        verify(results).confirmResult(any(), eq(data.tournament().id()), eq(firstMatch.id()));
        verify(results).rejectResult(any(), eq(data.tournament().id()), eq(firstMatch.id()));
    }

    @Test
    void adminDecidesResult() throws Exception {
        when(results.decideResult(any(), any(), any(), any())).thenReturn(data.view());

        mvc.perform(put(base + "/{m}/result", tournamentId, matchId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": 8, "goalsB": 10}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(results).decideResult(actor.capture(), any(), eq(firstMatch.id()), eq(new MatchResult(8, 10)));
        assertThat(actor.getValue().admin()).isTrue();
    }

    @Test
    void decidingAsNormalUserIsForbidden() throws Exception {
        when(results.decideResult(any(), any(), any(), any())).thenThrow(new ForbiddenException("Nur Admins dürfen das"));

        mvc.perform(put(base + "/{m}/result", tournamentId, matchId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"goalsA": 8, "goalsB": 10}"""))
                .andExpect(status().isForbidden());
    }
}
