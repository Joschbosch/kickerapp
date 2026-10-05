package zur.koeln.kickertool.domain.tournament;

/** Wie ein bestätigtes Ergebnis zustande kam. */
public enum ResultSource {
    /** Eingetragen von einem Team, bestätigt vom Gegnerteam. */
    TEAM,
    /** Vom Admin festgelegt oder korrigiert. */
    ADMIN
}
