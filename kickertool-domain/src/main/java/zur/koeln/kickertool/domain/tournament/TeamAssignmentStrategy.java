package zur.koeln.kickertool.domain.tournament;

import java.util.List;

/**
 * Teilt die aktiven Spieler einer neuen Runde in Teams und Matches ein. Die Reihenfolge der Rückgabe ist die
 * Reihenfolge, in der die Matches an die Tische kommen.
 *
 * <p>Vertrag: Jeder aktive Spieler kommt genau einmal vor. Fehlen Spieler zu voller Vierergruppe, werden
 * {@link DummySlot}s eingesetzt (höchstens {@value Tournament#MAX_DUMMIES}). Die Einspringer setzt das
 * System erst, wenn das Match an einen Tisch kommt.
 */
public interface TeamAssignmentStrategy {

    List<Pairing> assign(AssignmentContext context);
}
