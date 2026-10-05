package zur.koeln.kickertool.application.port.out;

import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Verteilt Turnier-Events an interessierte Clients. Die Implementierung ist austauschbar (z. B. In-Memory für
 * eine einzelne Instanz, später Redis oder ein Broker). Wird innerhalb einer Transaktion veröffentlicht, soll
 * die Auslieferung erst nach dem Commit erfolgen.
 */
public interface TournamentEventBus {

    void publish(TournamentEvent event);

    EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener);
}
