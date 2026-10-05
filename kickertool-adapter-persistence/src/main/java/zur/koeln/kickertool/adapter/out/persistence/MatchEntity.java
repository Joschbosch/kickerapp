package zur.koeln.kickertool.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import zur.koeln.kickertool.domain.tournament.MatchStatus;
import zur.koeln.kickertool.domain.tournament.ResultSource;
import zur.koeln.kickertool.domain.tournament.Side;

@Entity
@Table(name = "tournament_match")
class MatchEntity {

    @Id
    UUID id;

    @Column(name = "tournament_id", nullable = false)
    UUID tournamentId;

    @Column(name = "round_number", nullable = false)
    int roundNumber;

    @Column(name = "queue_position", nullable = false)
    int queuePosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MatchStatus status;

    @Column(name = "table_number")
    Integer tableNumber;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "dummy", column = @Column(name = "slot_a1_dummy", nullable = false)),
            @AttributeOverride(name = "playerId", column = @Column(name = "slot_a1_player"))})
    SlotColumns slotA1 = new SlotColumns();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "dummy", column = @Column(name = "slot_a2_dummy", nullable = false)),
            @AttributeOverride(name = "playerId", column = @Column(name = "slot_a2_player"))})
    SlotColumns slotA2 = new SlotColumns();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "dummy", column = @Column(name = "slot_b1_dummy", nullable = false)),
            @AttributeOverride(name = "playerId", column = @Column(name = "slot_b1_player"))})
    SlotColumns slotB1 = new SlotColumns();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "dummy", column = @Column(name = "slot_b2_dummy", nullable = false)),
            @AttributeOverride(name = "playerId", column = @Column(name = "slot_b2_player"))})
    SlotColumns slotB2 = new SlotColumns();

    @Column(name = "goals_a")
    Integer goalsA;

    @Column(name = "goals_b")
    Integer goalsB;

    @Column(name = "result_entered_by")
    UUID resultEnteredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_entered_side")
    Side resultEnteredSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_source")
    ResultSource resultSource;

    protected MatchEntity() {
    }

    MatchEntity(UUID id, UUID tournamentId) {
        this.id = id;
        this.tournamentId = tournamentId;
    }
}
