package zur.koeln.kickertool.application.port.in;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.TournamentId;

public interface MatchResultUseCase {

    /** Ein Spieler des Matches trägt das Ergebnis ein. Das Gegnerteam muss bestätigen. */
    TournamentView submitResult(Actor actor, TournamentId tournamentId, MatchId matchId, MatchResult result);

    /** Ein Spieler des Gegnerteams bestätigt das eingetragene Ergebnis. */
    TournamentView confirmResult(Actor actor, TournamentId tournamentId, MatchId matchId);

    /** Ein Spieler des Gegnerteams lehnt ab. Der Admin entscheidet dann. */
    TournamentView rejectResult(Actor actor, TournamentId tournamentId, MatchId matchId);

    /** Nur für Admins: Ergebnis festlegen oder auch nachträglich korrigieren. */
    TournamentView decideResult(Actor actor, TournamentId tournamentId, MatchId matchId, MatchResult result);
}
