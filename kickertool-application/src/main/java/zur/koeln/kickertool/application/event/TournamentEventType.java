package zur.koeln.kickertool.application.event;

public enum TournamentEventType {
    /** Name, Datum, Konfiguration oder Status des Turniers haben sich geändert. */
    TOURNAMENT_CHANGED,
    /** Anmeldungen, Pausen oder Ausscheiden haben sich geändert. */
    PARTICIPANTS_CHANGED,
    /** Eine neue Runde wurde ausgelost. */
    ROUND_STARTED,
    /** Matches haben sich geändert (Tische, Ergebnisse, Status). */
    MATCHES_CHANGED,
    /** Die Rangliste hat sich geändert. */
    RANKING_CHANGED,
    /**
     * Der Client hat Events verpasst, die sich nicht mehr nachliefern lassen (zu lange weg oder der Server wurde neu
     * gestartet). Er muss den Stand komplett neu laden.
     */
    RESYNC
}
