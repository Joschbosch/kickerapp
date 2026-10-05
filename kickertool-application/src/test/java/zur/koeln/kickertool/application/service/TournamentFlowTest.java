package zur.koeln.kickertool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.ForbiddenException;
import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.NotPermittedException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.MatchStatus;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.SwissDypTeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.StandInSuggester;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

class TournamentFlowTest {

    private final InMemoryRepositories.Players players = new InMemoryRepositories.Players();
    private final InMemoryRepositories.Tournaments tournaments = new InMemoryRepositories.Tournaments();
    private final InMemoryRepositories.RecordingEventBus eventBus = new InMemoryRepositories.RecordingEventBus();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);
    private final StandInSuggester suggester = new NearestRankStandInSuggester();

    private final PlayerService playerService = new PlayerService(players);
    private final TournamentManagementService management = new TournamentManagementService(
            tournaments, players, eventBus, clock, new SwissDypTeamAssignmentStrategy(), suggester);
    private final ParticipationService participation =
            new ParticipationService(tournaments, players, eventBus, clock);
    private final MatchResultService results =
            new MatchResultService(tournaments, players, eventBus, clock, suggester);
    private final TournamentQueryService queries = new TournamentQueryService(tournaments, players);

    private Actor admin;
    private final List<Actor> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Player adminPlayer = playerService.provision("kc-admin", "Admin");
        admin = new Actor(adminPlayer.id(), true);
    }

    private Actor newUser(String name) {
        Player p = playerService.provision("kc-" + name, name);
        Actor actor = new Actor(p.id(), false);
        users.add(actor);
        return actor;
    }

    private TournamentId planAndRegister(int playerCount, int tables) {
        TournamentView view = management.plan(admin, "Sommerturnier", LocalDate.of(2026, 10, 5),
                new TournamentConfig(tables, 3, 1, 0, 10, 5, null));
        TournamentId id = view.tournament().id();
        for (int i = 1; i <= playerCount; i++) {
            Actor u = newUser("Spieler" + i);
            participation.register(u, id, u.playerId());
        }
        return id;
    }

    private Actor actorFor(zur.koeln.kickertool.domain.player.PlayerId id) {
        return new Actor(id, false);
    }

    // ---------------------------------------------------------------- Berechtigungen

    @Test
    void onlyAdminsManageTournaments() {
        Actor user = newUser("Normalo");
        assertThatThrownBy(() -> management.plan(user, "X", LocalDate.now(), null))
                .isInstanceOf(ForbiddenException.class);

        TournamentId id = management.plan(admin, "X", LocalDate.now(), null).tournament().id();
        assertThatThrownBy(() -> management.start(user, id)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> management.startNextRound(user, id)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> management.updateConfig(user, id, TournamentConfig.defaults()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> management.finish(user, id)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> playerService.listPlayers(user)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void playersManageOnlyTheirOwnParticipationAdminsAnyones() {
        TournamentId id = management.plan(admin, "X", LocalDate.now(), null).tournament().id();
        Actor anna = newUser("Anna");
        Actor ben = newUser("Ben");

        assertThatThrownBy(() -> participation.register(anna, id, ben.playerId()))
                .isInstanceOf(ForbiddenException.class);
        participation.register(anna, id, anna.playerId());
        participation.register(admin, id, ben.playerId());

        assertThat(queries.getTournament(id).tournament().participants()).hasSize(2);
        assertThatThrownBy(() -> participation.unregister(anna, id, ben.playerId()))
                .isInstanceOf(ForbiddenException.class);
        participation.unregister(anna, id, anna.playerId());
        assertThat(queries.getTournament(id).tournament().participants()).hasSize(1);
    }

    @Test
    void adminCannotRegisterUnknownPlayer() {
        TournamentId id = management.plan(admin, "X", LocalDate.now(), null).tournament().id();
        assertThatThrownBy(() -> participation.register(admin, id,
                zur.koeln.kickertool.domain.player.PlayerId.random())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownTournamentIsNotFound() {
        assertThatThrownBy(() -> queries.getTournament(TournamentId.random())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> management.start(admin, TournamentId.random())).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- Ablauf

    @Test
    void fullRoundWithDummiesAndTables() {
        TournamentId id = planAndRegister(10, 2);
        management.start(admin, id);
        TournamentView view = management.startNextRound(admin, id);

        var round = view.tournament().currentRound().orElseThrow();
        assertThat(round.matches()).hasSize(3);
        assertThat(round.matches().get(0).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(round.matches().get(1).status()).isEqualTo(MatchStatus.ON_TABLE);
        assertThat(round.matches().get(2).status()).isEqualTo(MatchStatus.QUEUED);

        // Welle 1: Team A trägt ein, Team B bestätigt
        for (int guard = 0; guard < 5 && !queries.getTournament(id).tournament().currentRound().orElseThrow().isComplete(); guard++) {
            for (Match m : queries.getTournament(id).tournament().currentRound().orElseThrow().matches()) {
                if (m.status() == MatchStatus.ON_TABLE) {
                    results.submitResult(actorFor(m.teamA().actingPlayers().get(0)), id, m.id(), new MatchResult(10, 6));
                    results.confirmResult(actorFor(m.teamB().actingPlayers().get(0)), id, m.id());
                }
            }
        }

        TournamentView after = queries.getTournament(id);
        assertThat(after.tournament().currentRound().orElseThrow().isComplete()).isTrue();
        assertThat(after.tournament().ranking().entries()).hasSize(10);
        assertThat(after.tournament().ranking().entries()).allMatch(e -> e.matchesPlayed() == 1);
        assertThat(after.players()).hasSize(10);

        management.startNextRound(admin, id);
        assertThat(queries.getTournament(id).tournament().rounds()).hasSize(2);
    }

    @Test
    void roundCannotStartWhileResultsAreMissing() {
        TournamentId id = planAndRegister(8, 2);
        management.start(admin, id);
        management.startNextRound(admin, id);

        assertThatThrownBy(() -> management.startNextRound(admin, id)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> management.finish(admin, id)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void resultEntryRulesApplyThroughTheUseCases() {
        TournamentId id = planAndRegister(8, 2);
        management.start(admin, id);
        Match match = management.startNextRound(admin, id).tournament().currentRound().orElseThrow().matches().get(0);
        Actor teamA = actorFor(match.teamA().actingPlayers().get(0));
        Actor teamB = actorFor(match.teamB().actingPlayers().get(0));
        Actor outsider = actorFor(
                queries.getTournament(id).tournament().currentRound().orElseThrow().matches().get(1)
                        .teamA().actingPlayers().get(0));

        assertThatThrownBy(() -> results.submitResult(outsider, id, match.id(), new MatchResult(10, 1)))
                .isInstanceOf(NotPermittedException.class);
        assertThatThrownBy(() -> results.submitResult(teamA, id, match.id(), new MatchResult(11, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        results.submitResult(teamA, id, match.id(), new MatchResult(10, 1));
        assertThatThrownBy(() -> results.confirmResult(teamA, id, match.id()))
                .isInstanceOf(NotPermittedException.class);
        results.rejectResult(teamB, id, match.id());

        assertThat(match.status()).isEqualTo(MatchStatus.DISPUTED);
        assertThatThrownBy(() -> results.decideResult(teamA, id, match.id(), new MatchResult(10, 1)))
                .isInstanceOf(ForbiddenException.class);
        results.decideResult(admin, id, match.id(), new MatchResult(8, 10));
        assertThat(match.status()).isEqualTo(MatchStatus.CONFIRMED);
    }

    @Test
    void participantStatusChangesAreAppliedAndPublished() {
        TournamentId id = planAndRegister(8, 2);
        management.start(admin, id);
        management.startNextRound(admin, id);
        Actor anna = users.get(0);

        participation.pause(anna, id, anna.playerId());
        TournamentView view = participation.withdraw(admin, id, anna.playerId());

        assertThat(view.tournament().findParticipant(anna.playerId()).orElseThrow().status().name())
                .isEqualTo("WITHDRAWN");
        assertThat(eventBus.events).extracting(TournamentEvent::type)
                .contains(TournamentEventType.PARTICIPANTS_CHANGED, TournamentEventType.RANKING_CHANGED);
    }

    @Test
    void listsTournamentSummaries() {
        planAndRegister(3, 1);
        assertThat(queries.listTournaments()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Sommerturnier");
            assertThat(s.participantCount()).isEqualTo(3);
            assertThat(s.status()).isEqualTo(TournamentStatus.PLANNED);
        });
    }

    // ---------------------------------------------------------------- Events

    @Test
    void publishesEventsForTournamentChanges() {
        TournamentId id = planAndRegister(8, 2);
        eventBus.events.clear();

        management.start(admin, id);
        management.startNextRound(admin, id);

        assertThat(eventBus.events).extracting(TournamentEvent::type).containsExactly(
                TournamentEventType.TOURNAMENT_CHANGED, TournamentEventType.ROUND_STARTED,
                TournamentEventType.MATCHES_CHANGED);
        assertThat(eventBus.events).allMatch(e -> e.tournamentId().equals(id));
    }

    @Test
    void resultConfirmationPublishesMatchAndRankingEvents() {
        TournamentId id = planAndRegister(8, 2);
        management.start(admin, id);
        Match match = management.startNextRound(admin, id).tournament().currentRound().orElseThrow().matches().get(0);
        results.submitResult(actorFor(match.teamA().actingPlayers().get(0)), id, match.id(), new MatchResult(10, 3));
        eventBus.events.clear();

        results.confirmResult(actorFor(match.teamB().actingPlayers().get(0)), id, match.id());

        assertThat(eventBus.events).extracting(TournamentEvent::type)
                .containsExactly(TournamentEventType.MATCHES_CHANGED, TournamentEventType.RANKING_CHANGED);
        assertThat(eventBus.events.get(0).matchId()).isEqualTo(match.id());
    }

    @Test
    void eventSubscriptionRequiresExistingTournamentAndDeliversEvents() {
        TournamentEventService events = new TournamentEventService(tournaments, eventBus);
        assertThatThrownBy(() -> events.subscribe(TournamentId.random(), e -> { }))
                .isInstanceOf(NotFoundException.class);

        TournamentId id = planAndRegister(4, 1);
        List<TournamentEvent> received = new ArrayList<>();
        try (var subscription = events.subscribe(id, received::add)) {
            management.start(admin, id);
        }
        assertThat(received).extracting(TournamentEvent::type).containsExactly(TournamentEventType.TOURNAMENT_CHANGED);
    }

    // ---------------------------------------------------------------- Spieler-Provisionierung

    @Test
    void provisioningCreatesPlayerOnceAndUpdatesTheName() {
        Player first = playerService.provision("sub-1", "Anna");
        Player again = playerService.provision("sub-1", "Anna");
        Player renamed = playerService.provision("sub-1", "Anna Schmidt");

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(renamed.id()).isEqualTo(first.id());
        assertThat(renamed.displayName()).isEqualTo("Anna Schmidt");
        assertThat(players.findBySubject("sub-1")).contains(renamed);
    }

    @Test
    void provisioningToleratesConcurrentFirstLogin() {
        players.failNextInsert = true;

        Player player = playerService.provision("sub-race", "Anna");

        assertThat(player.subject()).isEqualTo("sub-race");
        assertThat(player.displayName()).isEqualTo("Konkurrierend angelegt");
    }
}
