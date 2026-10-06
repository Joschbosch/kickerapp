package zur.koeln.kickertool.application.port.out;

import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Verteilt Turnier-Events an interessierte Clients. Die Implementierung ist austauschbar (z. B. In-Memory für
 * eine einzelne Instanz, später Redis oder ein Broker). Wird innerhalb einer Transaktion veröffentlicht, soll
 * die Auslieferung erst nach dem Commit erfolgen.
 *
 * <p>Die Implementierung vergibt jedem ausgelieferten Event eine {@link TournamentEvent#id() Kennung} und hält die
 * letzten Events je Turnier vor, damit sich ein Client nach einem Verbindungsabbruch nachliefern lassen kann.
 */
public interface TournamentEventBus {

    void publish(TournamentEvent event);

    /**
     * Abonniert die Events eines Turniers.
     *
     * @param lastEventId Kennung des letzten Events, das der Client erhalten hat, oder {@code null} bei einer neuen
     *                    Verbindung. Sonst werden zuerst die verpassten Events nachgeliefert. Geht das nicht mehr
     *                    (Lücke zu groß, Neustart, unbekannte Kennung), kommt stattdessen ein einzelnes
     *                    {@link zur.koeln.kickertool.application.event.TournamentEventType#RESYNC RESYNC}.
     */
    EventSubscription subscribe(TournamentId tournamentId, String lastEventId, Consumer<TournamentEvent> listener);

    /** Neue Verbindung ohne Nachliefern. */
    default EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener) {
        return subscribe(tournamentId, null, listener);
    }
}
