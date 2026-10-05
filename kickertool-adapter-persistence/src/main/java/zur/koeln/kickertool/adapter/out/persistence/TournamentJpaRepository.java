package zur.koeln.kickertool.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TournamentJpaRepository extends JpaRepository<TournamentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TournamentEntity t where t.id = :id")
    Optional<TournamentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select new zur.koeln.kickertool.adapter.out.persistence.TournamentSummaryRow(
                t.id, t.name, t.date, t.status,
                (select count(p) from ParticipantEntity p where p.tournamentId = t.id),
                (select coalesce(max(m.roundNumber), 0) from MatchEntity m where m.tournamentId = t.id))
            from TournamentEntity t
            order by t.date desc, t.name
            """)
    List<TournamentSummaryRow> findAllSummaries();
}
