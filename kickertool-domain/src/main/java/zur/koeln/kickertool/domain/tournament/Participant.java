package zur.koeln.kickertool.domain.tournament;

import java.time.Instant;
import java.util.Objects;

import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.PlayerId;

/** Ein Spieler im Kontext eines Turniers. */
public final class Participant {

    private final PlayerId playerId;
    private final Instant registeredAt;
    private ParticipantStatus status;

    private Participant(PlayerId playerId, Instant registeredAt, ParticipantStatus status) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.status = Objects.requireNonNull(status, "status");
    }

    static Participant register(PlayerId playerId, Instant now) {
        return new Participant(playerId, now, ParticipantStatus.ACTIVE);
    }

    /** Rekonstruktion aus der Persistenz. */
    public static Participant restore(PlayerId playerId, Instant registeredAt, ParticipantStatus status) {
        return new Participant(playerId, registeredAt, status);
    }

    void pause() {
        if (status != ParticipantStatus.ACTIVE) {
            throw new RuleViolationException("Nur aktive Spieler können pausieren (Status: " + status + ")");
        }
        status = ParticipantStatus.PAUSED;
    }

    void resume() {
        if (status != ParticipantStatus.PAUSED) {
            throw new RuleViolationException("Nur pausierte Spieler können wieder einsteigen (Status: " + status + ")");
        }
        status = ParticipantStatus.ACTIVE;
    }

    void withdraw() {
        if (status == ParticipantStatus.WITHDRAWN) {
            throw new RuleViolationException("Der Spieler ist bereits ausgeschieden");
        }
        status = ParticipantStatus.WITHDRAWN;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public ParticipantStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == ParticipantStatus.ACTIVE;
    }
}
