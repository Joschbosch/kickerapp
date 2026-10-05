package zur.koeln.kickertool.domain.tournament;

public enum TournamentStatus {
    /** Geplant, Anmeldung und Konfiguration möglich. */
    PLANNED,
    /** Turnier läuft, Runden werden gespielt. */
    RUNNING,
    /** Beendet. */
    FINISHED
}
