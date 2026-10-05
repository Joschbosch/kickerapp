package zur.koeln.kickertool.application.port.in;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Anmeldung und Teilnahmestatus. Jeweils für den Spieler selbst oder einen Admin. */
public interface ParticipationUseCase {

    TournamentView register(Actor actor, TournamentId tournamentId, PlayerId player);

    /** Nur vor dem Start. */
    TournamentView unregister(Actor actor, TournamentId tournamentId, PlayerId player);

    /** Wirkt ab der nächsten Runde. */
    TournamentView pause(Actor actor, TournamentId tournamentId, PlayerId player);

    TournamentView resume(Actor actor, TournamentId tournamentId, PlayerId player);

    /** Scheidet ab der nächsten Runde aus, Punkte bleiben erhalten. */
    TournamentView withdraw(Actor actor, TournamentId tournamentId, PlayerId player);
}
