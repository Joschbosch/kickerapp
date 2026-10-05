package zur.koeln.kickertool.domain.tournament;

/** Ein Platz in einem Team: entweder ein echter Turnierspieler oder ein Dummy. */
public sealed interface Slot permits PlayerSlot, DummySlot {
}
