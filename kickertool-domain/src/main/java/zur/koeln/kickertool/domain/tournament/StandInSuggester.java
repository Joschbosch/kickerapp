package zur.koeln.kickertool.domain.tournament;

import java.util.Collection;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Schlägt für einen Dummy-Platz einen Turnierteilnehmer vor, der aktuell nicht an einem Tisch steht und in
 * der Rangliste nahe den anderen Spielern des Matches liegt.
 */
public interface StandInSuggester {

    /**
     * @param match      das Match, das an einen Tisch kommt
     * @param ranking    aktuelle Rangliste
     * @param candidates aktive Teilnehmer, die gerade nicht an einem Tisch stehen und noch nicht einspringen
     * @return der Vorschlag, leer wenn niemand verfügbar ist (dann springt ein Dritter ein)
     */
    Optional<PlayerId> suggest(Match match, Ranking ranking, Collection<PlayerId> candidates);
}
