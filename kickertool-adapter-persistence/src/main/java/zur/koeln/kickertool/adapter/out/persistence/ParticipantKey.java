package zur.koeln.kickertool.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Zusammengesetzter Schlüssel (Turnier, Spieler) der Teilnahme. */
class ParticipantKey implements Serializable {

    UUID tournamentId;
    UUID playerId;

    protected ParticipantKey() {
    }

    ParticipantKey(UUID tournamentId, UUID playerId) {
        this.tournamentId = tournamentId;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParticipantKey other
                && Objects.equals(tournamentId, other.tournamentId)
                && Objects.equals(playerId, other.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, playerId);
    }
}
