package zur.koeln.kickertool.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MatchJpaRepository extends JpaRepository<MatchEntity, UUID> {

    List<MatchEntity> findByTournamentIdOrderByRoundNumberAscQueuePositionAsc(UUID tournamentId);
}
