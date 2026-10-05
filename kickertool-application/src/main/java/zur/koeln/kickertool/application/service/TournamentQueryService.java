package zur.koeln.kickertool.application.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.port.in.TournamentQueries;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@Transactional(readOnly = true)
public class TournamentQueryService implements TournamentQueries {

    private final TournamentSupport support;
    private final TournamentRepository tournaments;

    public TournamentQueryService(TournamentRepository tournaments, PlayerRepository players) {
        // Lesender Zugriff: weder Events noch Zeit werden gebraucht
        this.support = new TournamentSupport(tournaments, players, null, null);
        this.tournaments = tournaments;
    }

    @Override
    public List<TournamentSummary> listTournaments() {
        return tournaments.findAllSummaries();
    }

    @Override
    public TournamentView getTournament(TournamentId id) {
        return support.view(support.load(id));
    }
}
