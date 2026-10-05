package zur.koeln.kickertool.application.port.in;

import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Live-Updates: Clients lassen sich über Änderungen eines Turniers benachrichtigen. */
public interface TournamentEventUseCase {

    EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener);
}
