package zur.koeln.kickertool.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import zur.koeln.kickertool.domain.tournament.TournamentStatus;

@Entity
@Table(name = "tournament")
class TournamentEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(name = "event_date", nullable = false)
    LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TournamentStatus status;

    @Column(name = "table_count", nullable = false)
    int tableCount;

    @Column(name = "points_win", nullable = false)
    int pointsWin;

    @Column(name = "points_draw", nullable = false)
    int pointsDraw;

    @Column(name = "points_loss", nullable = false)
    int pointsLoss;

    @Column(name = "goal_limit", nullable = false)
    int goalLimit;

    @Column(name = "match_minutes", nullable = false)
    int matchMinutes;

    @Column(name = "planned_rounds")
    Integer plannedRounds;

    @Column(name = "random_rounds", nullable = false)
    int randomRounds;

    protected TournamentEntity() {
    }

    TournamentEntity(UUID id) {
        this.id = id;
    }
}
