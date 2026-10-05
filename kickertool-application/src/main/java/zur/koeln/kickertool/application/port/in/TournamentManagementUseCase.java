package zur.koeln.kickertool.application.port.in;

import java.time.LocalDate;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Turnier planen, konfigurieren und durch die Runden führen. Alles nur für Admins. */
public interface TournamentManagementUseCase {

    /** @param config Konfiguration, {@code null} für die Standardwerte */
    TournamentView plan(Actor actor, String name, LocalDate date, TournamentConfig config);

    TournamentView updateDetails(Actor actor, TournamentId id, String name, LocalDate date);

    /** Auch während des Turniers möglich, z. B. wenn ein Tisch ausfällt. */
    TournamentView updateConfig(Actor actor, TournamentId id, TournamentConfig config);

    TournamentView start(Actor actor, TournamentId id);

    /** Lost die nächste Runde aus. Erst möglich, wenn alle Ergebnisse der letzten Runde bestätigt sind. */
    TournamentView startNextRound(Actor actor, TournamentId id);

    TournamentView finish(Actor actor, TournamentId id);
}
