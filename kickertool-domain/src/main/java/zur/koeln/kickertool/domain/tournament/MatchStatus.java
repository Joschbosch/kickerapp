package zur.koeln.kickertool.domain.tournament;

public enum MatchStatus {
    /** Ausgelost, wartet auf einen freien Tisch. */
    QUEUED,
    /** Spielt gerade an einem Tisch, Ergebnis fehlt noch. */
    ON_TABLE,
    /** Ein Team hat ein Ergebnis eingetragen, das Gegnerteam muss bestätigen. */
    RESULT_ENTERED,
    /** Das Gegnerteam hat das Ergebnis abgelehnt, der Admin entscheidet. */
    DISPUTED,
    /** Ergebnis steht fest und zählt für die Rangliste. */
    CONFIRMED
}
