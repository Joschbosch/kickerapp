package zur.koeln.kickertool.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import zur.koeln.kickertool.domain.tournament.ParticipantStatus;

@Entity
@Table(name = "participant")
@IdClass(ParticipantKey.class)
class ParticipantEntity {

    @Id
    @Column(name = "tournament_id")
    UUID tournamentId;

    @Id
    @Column(name = "player_id")
    UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ParticipantStatus status;

    @Column(name = "registered_at", nullable = false)
    Instant registeredAt;

    protected ParticipantEntity() {
    }

    ParticipantEntity(UUID tournamentId, UUID playerId) {
        this.tournamentId = tournamentId;
        this.playerId = playerId;
    }
}
