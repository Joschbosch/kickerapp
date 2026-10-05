package zur.koeln.kickertool.application.port.in;

import java.util.List;

import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.TournamentId;

public interface TournamentQueries {

    List<TournamentSummary> listTournaments();

    /** Das Turnier mit Teilnehmern, Runden und Matches. Die Rangliste ergibt sich aus {@code tournament.ranking()}. */
    TournamentView getTournament(TournamentId id);
}
