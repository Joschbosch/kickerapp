package zur.koeln.kickertool.domain.tournament;

/**
 * Konfiguration eines Turniers. Kann bis zum Ende des Turniers geändert werden.
 *
 * @param tableCount     Anzahl verfügbarer Tische; gilt ab der nächsten Welle
 * @param pointsWin      Punkte für einen Sieg
 * @param pointsDraw     Punkte für ein Unentschieden
 * @param pointsLoss     Punkte für eine Niederlage
 * @param goalLimit      Tore, bei denen ein Match endet (ein Satz)
 * @param matchMinutes   Maximale Spielzeit in Minuten (ein Satz)
 * @param plannedRounds  Geplante Rundenzahl, {@code null} = offen. Dient nur der Anzeige.
 * @param randomRounds   Anzahl der ersten Runden, die komplett zufällig ausgelost werden. Danach wird nach
 *                       Rangliste ausgelost. Bezieht sich auf die Rundennummer, gilt also auch rückwirkend, wenn
 *                       der Wert während des Turniers geändert wird.
 */
public record TournamentConfig(
        int tableCount,
        int pointsWin,
        int pointsDraw,
        int pointsLoss,
        int goalLimit,
        int matchMinutes,
        Integer plannedRounds,
        int randomRounds) {

    public static final int DEFAULT_TABLE_COUNT = 1;
    public static final int DEFAULT_POINTS_WIN = 3;
    public static final int DEFAULT_POINTS_DRAW = 1;
    public static final int DEFAULT_POINTS_LOSS = 0;
    public static final int DEFAULT_GOAL_LIMIT = 10;
    public static final int DEFAULT_MATCH_MINUTES = 5;
    public static final int DEFAULT_RANDOM_ROUNDS = 2;

    public TournamentConfig {
        if (tableCount < 1) {
            throw new IllegalArgumentException("tableCount must be at least 1");
        }
        if (pointsWin < 0 || pointsDraw < 0 || pointsLoss < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
        if (goalLimit < 1) {
            throw new IllegalArgumentException("goalLimit must be at least 1");
        }
        if (matchMinutes < 1) {
            throw new IllegalArgumentException("matchMinutes must be at least 1");
        }
        if (plannedRounds != null && plannedRounds < 1) {
            throw new IllegalArgumentException("plannedRounds must be at least 1 or null");
        }
        if (randomRounds < 0) {
            throw new IllegalArgumentException("randomRounds must not be negative");
        }
    }

    /** Wie die vollständige Form, mit dem Standardwert für {@code randomRounds}. */
    public TournamentConfig(int tableCount, int pointsWin, int pointsDraw, int pointsLoss, int goalLimit,
            int matchMinutes, Integer plannedRounds) {
        this(tableCount, pointsWin, pointsDraw, pointsLoss, goalLimit, matchMinutes, plannedRounds,
                DEFAULT_RANDOM_ROUNDS);
    }

    public static TournamentConfig defaults() {
        return new TournamentConfig(DEFAULT_TABLE_COUNT, DEFAULT_POINTS_WIN, DEFAULT_POINTS_DRAW,
                DEFAULT_POINTS_LOSS, DEFAULT_GOAL_LIMIT, DEFAULT_MATCH_MINUTES, null, DEFAULT_RANDOM_ROUNDS);
    }
}
