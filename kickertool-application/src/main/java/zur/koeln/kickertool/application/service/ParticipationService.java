package zur.koeln.kickertool.application.service;

import java.time.Clock;
import java.util.function.Consumer;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.in.ParticipationUseCase;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@Transactional
public class ParticipationService implements ParticipationUseCase {

    private final TournamentSupport support;

    public ParticipationService(TournamentRepository tournaments, PlayerRepository players,
            TournamentEventBus eventBus, Clock clock) {
        this.support = new TournamentSupport(tournaments, players, eventBus, clock);
    }

    @Override
    public TournamentView register(Actor actor, TournamentId tournamentId, PlayerId player) {
        actor.requireSelfOrAdmin(player);
        support.requirePlayer(player);
        return change(tournamentId, tournament -> tournament.register(player, support.clock().instant()));
    }

    @Override
    public TournamentView unregister(Actor actor, TournamentId tournamentId, PlayerId player) {
        actor.requireSelfOrAdmin(player);
        return change(tournamentId, tournament -> tournament.unregister(player));
    }

    @Override
    public TournamentView pause(Actor actor, TournamentId tournamentId, PlayerId player) {
        actor.requireSelfOrAdmin(player);
        return change(tournamentId, tournament -> tournament.pause(player));
    }

    @Override
    public TournamentView resume(Actor actor, TournamentId tournamentId, PlayerId player) {
        actor.requireSelfOrAdmin(player);
        return change(tournamentId, tournament -> tournament.resume(player));
    }

    @Override
    public TournamentView withdraw(Actor actor, TournamentId tournamentId, PlayerId player) {
        actor.requireSelfOrAdmin(player);
        return change(tournamentId, tournament -> tournament.withdraw(player));
    }

    private TournamentView change(TournamentId tournamentId, Consumer<Tournament> change) {
        Tournament tournament = support.loadForUpdate(tournamentId);
        change.accept(tournament);
        support.save(tournament);
        support.publish(tournamentId, TournamentEventType.PARTICIPANTS_CHANGED);
        support.publish(tournamentId, TournamentEventType.RANKING_CHANGED);
        return support.view(tournament);
    }
}
