package zur.koeln.kickertool.application.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.in.TournamentManagementUseCase;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.StandInSuggester;
import zur.koeln.kickertool.domain.tournament.TeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@Transactional
public class TournamentManagementService implements TournamentManagementUseCase {

    private final TournamentSupport support;
    private final TeamAssignmentStrategy assignmentStrategy;
    private final StandInSuggester standInSuggester;

    public TournamentManagementService(TournamentRepository tournaments, PlayerRepository players,
            TournamentEventBus eventBus, Clock clock, TeamAssignmentStrategy assignmentStrategy,
            StandInSuggester standInSuggester) {
        this.support = new TournamentSupport(tournaments, players, eventBus, clock);
        this.assignmentStrategy = assignmentStrategy;
        this.standInSuggester = standInSuggester;
    }

    @Override
    public TournamentView plan(Actor actor, String name, LocalDate date, TournamentConfig config) {
        actor.requireAdmin();
        Tournament tournament = Tournament.plan(TournamentId.random(), name, date,
                config != null ? config : TournamentConfig.defaults());
        support.save(tournament);
        support.publish(tournament.id(), TournamentEventType.TOURNAMENT_CHANGED);
        return support.view(tournament);
    }

    @Override
    public TournamentView updateDetails(Actor actor, TournamentId id, String name, LocalDate date) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(id);
        tournament.updateDetails(name, date);
        return saveAndPublish(tournament, TournamentEventType.TOURNAMENT_CHANGED);
    }

    @Override
    public TournamentView updateConfig(Actor actor, TournamentId id, TournamentConfig config) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(id);
        tournament.updateConfig(config);
        return saveAndPublish(tournament, TournamentEventType.TOURNAMENT_CHANGED);
    }

    @Override
    public TournamentView start(Actor actor, TournamentId id) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(id);
        tournament.start();
        return saveAndPublish(tournament, TournamentEventType.TOURNAMENT_CHANGED);
    }

    @Override
    public TournamentView startNextRound(Actor actor, TournamentId id) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(id);
        tournament.startNextRound(assignmentStrategy, standInSuggester);
        support.save(tournament);
        support.publish(id, TournamentEventType.ROUND_STARTED);
        support.publish(id, TournamentEventType.MATCHES_CHANGED);
        return support.view(tournament);
    }

    @Override
    public TournamentView finish(Actor actor, TournamentId id) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(id);
        tournament.finish();
        return saveAndPublish(tournament, TournamentEventType.TOURNAMENT_CHANGED);
    }

    private TournamentView saveAndPublish(Tournament tournament, TournamentEventType type) {
        support.save(tournament);
        support.publish(tournament.id(), type);
        return support.view(tournament);
    }
}
