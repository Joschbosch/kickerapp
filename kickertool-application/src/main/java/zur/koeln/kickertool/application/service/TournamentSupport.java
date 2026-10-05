package zur.koeln.kickertool.application.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.event.TournamentEventType;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.Participant;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Gemeinsame Hilfen der Anwendungsdienste: Laden, Sperren, Lese-Modell bauen, Events veröffentlichen. */
final class TournamentSupport {

    private final TournamentRepository tournaments;
    private final PlayerRepository players;
    private final TournamentEventBus eventBus;
    private final Clock clock;

    TournamentSupport(TournamentRepository tournaments, PlayerRepository players, TournamentEventBus eventBus,
            Clock clock) {
        this.tournaments = tournaments;
        this.players = players;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    Clock clock() {
        return clock;
    }

    Tournament load(TournamentId id) {
        return tournaments.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Lädt das Turnier und sperrt es für die Dauer der Transaktion. */
    Tournament loadForUpdate(TournamentId id) {
        return tournaments.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    void save(Tournament tournament) {
        tournaments.save(tournament);
    }

    Player requirePlayer(PlayerId id) {
        return players.findById(id).orElseThrow(() -> new NotFoundException("Spieler nicht gefunden: " + id));
    }

    TournamentView view(Tournament tournament) {
        Set<PlayerId> ids = new HashSet<>();
        for (Participant participant : tournament.participants()) {
            ids.add(participant.playerId());
        }
        Map<PlayerId, Player> byId = players.findAllById(ids);
        return new TournamentView(tournament, byId);
    }

    void publish(TournamentId tournamentId, TournamentEventType type) {
        publish(tournamentId, type, null);
    }

    void publish(TournamentId tournamentId, TournamentEventType type, MatchId matchId) {
        eventBus.publish(new TournamentEvent(tournamentId, type, matchId, clock.instant()));
    }

    private static NotFoundException notFound(TournamentId id) {
        return new NotFoundException("Turnier nicht gefunden: " + id);
    }
}
