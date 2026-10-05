package zur.koeln.kickertool.application.service;

import java.time.Clock;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.in.MatchResultUseCase;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.StandInSuggester;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@Transactional
public class MatchResultService implements MatchResultUseCase {

    private final TournamentSupport support;
    private final StandInSuggester standInSuggester;

    public MatchResultService(TournamentRepository tournaments, PlayerRepository players,
            TournamentEventBus eventBus, Clock clock, StandInSuggester standInSuggester) {
        this.support = new TournamentSupport(tournaments, players, eventBus, clock);
        this.standInSuggester = standInSuggester;
    }

    @Override
    public TournamentView submitResult(Actor actor, TournamentId tournamentId, MatchId matchId, MatchResult result) {
        Tournament tournament = support.loadForUpdate(tournamentId);
        tournament.submitResult(matchId, actor.playerId(), result, standInSuggester);
        return saveAndPublish(tournament, matchId, false);
    }

    @Override
    public TournamentView confirmResult(Actor actor, TournamentId tournamentId, MatchId matchId) {
        Tournament tournament = support.loadForUpdate(tournamentId);
        tournament.confirmResult(matchId, actor.playerId());
        return saveAndPublish(tournament, matchId, true);
    }

    @Override
    public TournamentView rejectResult(Actor actor, TournamentId tournamentId, MatchId matchId) {
        Tournament tournament = support.loadForUpdate(tournamentId);
        tournament.rejectResult(matchId, actor.playerId());
        return saveAndPublish(tournament, matchId, false);
    }

    @Override
    public TournamentView decideResult(Actor actor, TournamentId tournamentId, MatchId matchId,
            MatchResult result) {
        actor.requireAdmin();
        Tournament tournament = support.loadForUpdate(tournamentId);
        tournament.decideResult(matchId, result, standInSuggester);
        return saveAndPublish(tournament, matchId, true);
    }

    private TournamentView saveAndPublish(Tournament tournament, MatchId matchId, boolean rankingChanged) {
        support.save(tournament);
        support.publish(tournament.id(), TournamentEventType.MATCHES_CHANGED, matchId);
        if (rankingChanged) {
            support.publish(tournament.id(), TournamentEventType.RANKING_CHANGED);
        }
        return support.view(tournament);
    }
}
