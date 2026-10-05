package zur.koeln.kickertool.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.port.out.EventSubscription;
import zur.koeln.kickertool.application.port.out.PlayerAlreadyExistsException;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    static class Players implements PlayerRepository {
        final Map<PlayerId, Player> store = new LinkedHashMap<>();
        /** Simuliert eine parallele Erstanmeldung: das erste Speichern eines neuen Spielers schlägt fehl. */
        boolean failNextInsert;

        @Override
        public Optional<Player> findById(PlayerId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Player> findBySubject(String subject) {
            return store.values().stream().filter(p -> p.subject().equals(subject)).findFirst();
        }

        @Override
        public Map<PlayerId, Player> findAllById(Collection<PlayerId> ids) {
            Map<PlayerId, Player> result = new HashMap<>();
            ids.forEach(id -> findById(id).ifPresent(p -> result.put(id, p)));
            return result;
        }

        @Override
        public List<Player> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public Player save(Player player) {
            if (failNextInsert && !store.containsKey(player.id())) {
                failNextInsert = false;
                store.put(PlayerId.random(), new Player(PlayerId.random(), player.subject(), "Konkurrierend angelegt"));
                throw new PlayerAlreadyExistsException("duplicate", null);
            }
            store.put(player.id(), player);
            return player;
        }
    }

    static class Tournaments implements TournamentRepository {
        final Map<TournamentId, Tournament> store = new LinkedHashMap<>();

        @Override
        public Optional<Tournament> findById(TournamentId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Tournament> findByIdForUpdate(TournamentId id) {
            return findById(id);
        }

        @Override
        public boolean exists(TournamentId id) {
            return store.containsKey(id);
        }

        @Override
        public List<TournamentSummary> findAllSummaries() {
            return store.values().stream()
                    .map(t -> new TournamentSummary(t.id(), t.name(), t.date(), t.status(), t.participants().size(),
                            t.rounds().size()))
                    .toList();
        }

        @Override
        public void save(Tournament tournament) {
            store.put(tournament.id(), tournament);
        }
    }

    static class RecordingEventBus implements TournamentEventBus {
        final List<TournamentEvent> events = new ArrayList<>();
        final Map<TournamentId, List<Consumer<TournamentEvent>>> listeners = new HashMap<>();

        @Override
        public void publish(TournamentEvent event) {
            events.add(event);
            listeners.getOrDefault(event.tournamentId(), List.of()).forEach(l -> l.accept(event));
        }

        @Override
        public EventSubscription subscribe(TournamentId tournamentId, Consumer<TournamentEvent> listener) {
            listeners.computeIfAbsent(tournamentId, k -> new ArrayList<>()).add(listener);
            return () -> listeners.get(tournamentId).remove(listener);
        }
    }
}
