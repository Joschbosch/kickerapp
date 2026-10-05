package zur.koeln.kickertool.domain.tournament;

import java.util.Optional;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Ein Dummy, der einen fehlenden Spieler ersetzt. Er wird wie ein unbekannter Spieler behandelt: Er bekommt
 * keine Punkte und taucht in keiner Rangliste für dieses Match auf.
 *
 * @param standIn Der Turnierteilnehmer, der tatsächlich spielt (Vorschlag des Systems). {@code null}, wenn
 *                keiner verfügbar war und ein Dritter aus dem Publikum einspringt.
 */
public record DummySlot(PlayerId standIn) implements Slot {

    public static DummySlot unassigned() {
        return new DummySlot(null);
    }

    public Optional<PlayerId> standInPlayer() {
        return Optional.ofNullable(standIn);
    }
}
