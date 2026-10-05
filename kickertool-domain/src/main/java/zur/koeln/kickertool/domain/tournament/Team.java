package zur.koeln.kickertool.domain.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Ein Team aus genau zwei Plätzen. */
public record Team(Slot first, Slot second) {

    public Team {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first instanceof PlayerSlot a && second instanceof PlayerSlot b && a.playerId().equals(b.playerId())) {
            throw new IllegalArgumentException("Ein Spieler kann nicht mit sich selbst im Team sein");
        }
    }

    public List<Slot> slots() {
        return List.of(first, second);
    }

    /** Die echten Spieler, für die das Ergebnis zählt. */
    public List<PlayerId> realPlayers() {
        List<PlayerId> result = new ArrayList<>(2);
        for (Slot slot : slots()) {
            if (slot instanceof PlayerSlot p) {
                result.add(p.playerId());
            }
        }
        return List.copyOf(result);
    }

    /** Alle, die am Tisch stehen und das Ergebnis eintragen oder bestätigen dürfen: echte Spieler und Einspringer. */
    public List<PlayerId> actingPlayers() {
        List<PlayerId> result = new ArrayList<>(2);
        for (Slot slot : slots()) {
            switch (slot) {
                case PlayerSlot p -> result.add(p.playerId());
                case DummySlot d -> d.standInPlayer().ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    public int dummyCount() {
        return (int) slots().stream().filter(DummySlot.class::isInstance).count();
    }

    Team withDummies(UnaryOperator<DummySlot> filler) {
        return new Team(fill(first, filler), fill(second, filler));
    }

    private static Slot fill(Slot slot, UnaryOperator<DummySlot> filler) {
        return slot instanceof DummySlot d ? filler.apply(d) : slot;
    }
}
