package zur.koeln.kickertool.adapter.out.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.Participant;
import zur.koeln.kickertool.domain.tournament.Round;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Speichert das Turnier-Aggregat über mehrere Tabellen. Beim Speichern wird mit dem bestehenden Stand
 * abgeglichen: Neues wird angelegt, Geändertes aktualisiert, Entferntes gelöscht.
 */
@Transactional
class JpaTournamentRepository implements TournamentRepository {

    private final TournamentJpaRepository tournaments;
    private final ParticipantJpaRepository participants;
    private final MatchJpaRepository matches;

    JpaTournamentRepository(TournamentJpaRepository tournaments, ParticipantJpaRepository participants,
            MatchJpaRepository matches) {
        this.tournaments = tournaments;
        this.participants = participants;
        this.matches = matches;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tournament> findById(TournamentId id) {
        return tournaments.findById(id.value()).map(this::assemble);
    }

    @Override
    public Optional<Tournament> findByIdForUpdate(TournamentId id) {
        return tournaments.findByIdForUpdate(id.value()).map(this::assemble);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(TournamentId id) {
        return tournaments.existsById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentSummary> findAllSummaries() {
        return tournaments.findAllSummaries().stream()
                .map(row -> new TournamentSummary(new TournamentId(row.id()), row.name(), row.date(), row.status(),
                        (int) row.participantCount(), row.roundCount()))
                .toList();
    }

    @Override
    public void save(Tournament tournament) {
        UUID id = tournament.id().value();

        TournamentEntity entity = tournaments.findById(id).orElseGet(() -> new TournamentEntity(id));
        TournamentMapper.copyTo(tournament, entity);
        tournaments.save(entity);

        syncParticipants(id, tournament);
        syncMatches(id, tournament);
    }

    private void syncParticipants(UUID tournamentId, Tournament tournament) {
        Map<UUID, ParticipantEntity> existing = new HashMap<>();
        participants.findByTournamentId(tournamentId).forEach(p -> existing.put(p.playerId, p));

        for (Participant participant : tournament.participants()) {
            UUID playerId = participant.playerId().value();
            ParticipantEntity entity = existing.remove(playerId);
            if (entity == null) {
                entity = new ParticipantEntity(tournamentId, playerId);
            }
            TournamentMapper.copyTo(participant, entity);
            participants.save(entity);
        }
        participants.deleteAll(existing.values());
    }

    private void syncMatches(UUID tournamentId, Tournament tournament) {
        Map<UUID, MatchEntity> existing = new HashMap<>();
        matches.findByTournamentIdOrderByRoundNumberAscQueuePositionAsc(tournamentId)
                .forEach(m -> existing.put(m.id, m));

        for (Round round : tournament.rounds()) {
            for (Match match : round.matches()) {
                MatchEntity entity = existing.remove(match.id().value());
                if (entity == null) {
                    entity = new MatchEntity(match.id().value(), tournamentId);
                }
                TournamentMapper.copyTo(match, entity);
                matches.save(entity);
            }
        }
        matches.deleteAll(existing.values());
    }

    private Tournament assemble(TournamentEntity entity) {
        return TournamentMapper.toDomain(entity, participants.findByTournamentId(entity.id),
                matches.findByTournamentIdOrderByRoundNumberAscQueuePositionAsc(entity.id));
    }
}
