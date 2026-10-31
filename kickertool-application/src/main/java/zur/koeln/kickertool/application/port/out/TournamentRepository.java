package zur.koeln.kickertool.application.port.out;

import java.util.List;
import java.util.Optional;

import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

public interface TournamentRepository {

    Optional<Tournament> findById(TournamentId id);

    /**
     * Lädt das Turnier zur Änderung und sperrt es bis zum Ende der Transaktion. Mehrere Handys tragen
     * gleichzeitig Ergebnisse ein, die Änderungen am Turnier müssen daher nacheinander laufen.
     */
    Optional<Tournament> findByIdForUpdate(TournamentId id);

    boolean exists(TournamentId id);

    List<TournamentSummary> findAllSummaries();

    /** Alle Turniere, in denen der Spieler angemeldet ist (oder war, wenn er ausgeschieden ist). */
    List<Tournament> findAllByParticipant(PlayerId playerId);

    void save(Tournament tournament);
}
