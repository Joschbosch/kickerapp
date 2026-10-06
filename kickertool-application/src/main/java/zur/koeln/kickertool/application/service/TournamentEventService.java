package zur.koeln.kickertool.application.service;

import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.in.TournamentEventUseCase;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.tournament.TournamentId;

public class TournamentEventService implements TournamentEventUseCase {

    private final TournamentRepository tournaments;
    private final TournamentEventBus eventBus;

    public TournamentEventService(TournamentRepository tournaments, TournamentEventBus eventBus) {
        this.tournaments = tournaments;
        this.eventBus = eventBus;
    }

    @Override
    public EventSubscription subscribe(TournamentId tournamentId, String lastEventId,
            Consumer<TournamentEvent> listener) {
        requireTournament(tournamentId);
        return eventBus.subscribe(tournamentId, lastEventId, listener);
    }

    @Override
    public void requireTournament(TournamentId tournamentId) {
        if (!tournaments.exists(tournamentId)) {
            throw new NotFoundException("Turnier nicht gefunden: " + tournamentId);
        }
    }
}
