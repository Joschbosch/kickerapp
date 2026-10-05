package zur.koeln.kickertool.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;

import zur.koeln.kickertool.application.port.out.PlayerAlreadyExistsException;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;

class JpaPlayerRepository implements PlayerRepository {

    private final PlayerJpaRepository players;

    JpaPlayerRepository(PlayerJpaRepository players) {
        this.players = players;
    }

    @Override
    public Optional<Player> findById(PlayerId id) {
        return players.findById(id.value()).map(JpaPlayerRepository::toDomain);
    }

    @Override
    public Optional<Player> findBySubject(String subject) {
        return players.findBySubject(subject).map(JpaPlayerRepository::toDomain);
    }

    @Override
    public Map<PlayerId, Player> findAllById(Collection<PlayerId> ids) {
        List<UUID> uuids = ids.stream().map(PlayerId::value).toList();
        return players.findAllById(uuids).stream()
                .map(JpaPlayerRepository::toDomain)
                .collect(Collectors.toMap(Player::id, Function.identity()));
    }

    @Override
    public List<Player> findAll() {
        return players.findAll().stream().map(JpaPlayerRepository::toDomain)
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()))
                .toList();
    }

    /** Bewusst ohne eigene Transaktion, damit ein Konflikt nicht eine äußere Transaktion verdirbt. */
    @Override
    public Player save(Player player) {
        try {
            players.saveAndFlush(new PlayerEntity(player.id().value(), player.subject(), player.displayName()));
            return player;
        } catch (DataIntegrityViolationException e) {
            throw new PlayerAlreadyExistsException("Spieler mit dieser Kennung existiert bereits", e);
        }
    }

    private static Player toDomain(PlayerEntity entity) {
        return new Player(new PlayerId(entity.id), entity.subject, entity.displayName);
    }
}
