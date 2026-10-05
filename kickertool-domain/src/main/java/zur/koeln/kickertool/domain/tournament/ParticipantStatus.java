package zur.koeln.kickertool.domain.tournament;

public enum ParticipantStatus {
    /** Nimmt an den Auslosungen teil. */
    ACTIVE,
    /** Wird ab der nächsten Runde nicht mehr zugelost, kann wieder einsteigen. */
    PAUSED,
    /** Ausgeschieden ("raus"). Punkte bleiben erhalten, Rückkehr nicht möglich. */
    WITHDRAWN
}
