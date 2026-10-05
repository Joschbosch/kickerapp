package zur.koeln.kickertool.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Embeddable;

/**
 * Ein Platz im Team. Echter Spieler: {@code dummy = false}, {@code playerId} = Spieler. Dummy:
 * {@code dummy = true}, {@code playerId} = Einspringer oder {@code null}.
 */
@Embeddable
class SlotColumns {

    boolean dummy;
    UUID playerId;

    protected SlotColumns() {
    }

    SlotColumns(boolean dummy, UUID playerId) {
        this.dummy = dummy;
        this.playerId = playerId;
    }
}
