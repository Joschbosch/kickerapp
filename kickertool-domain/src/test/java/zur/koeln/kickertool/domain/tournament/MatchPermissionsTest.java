package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import zur.koeln.kickertool.domain.player.PlayerId;

/** Wer darf wann was: die Regeln, die auch die API als Flags an die Clients meldet. */
class MatchPermissionsTest {

    private final PlayerId a1 = PlayerId.random();
    private final PlayerId a2 = PlayerId.random();
    private final PlayerId b1 = PlayerId.random();
    private final PlayerId standInOfB = PlayerId.random();
    private final PlayerId outsider = PlayerId.random();

    private Match match() {
        return Match.queued(MatchId.random(), 1, 1,
                new Team(new PlayerSlot(a1), new PlayerSlot(a2)),
                new Team(new PlayerSlot(b1), new DummySlot(null)));
    }

    /** Das Match am Tisch, der Dummy von Team B bekommt {@code standInOfB} als Einspringer. */
    private Match onTable() {
        Match match = match();
        match.placeOnTable(1, (UnaryOperator<DummySlot>) dummy -> new DummySlot(standInOfB));
        return match;
    }

    @Test
    void waitingMatchAllowsNothing() {
        Match match = match();

        assertThat(match.canEnterResult(a1)).isFalse();
        assertThat(match.canRespondToResult(b1)).isFalse();
        assertThat(match.canBeDecidedByAdmin()).isFalse();
    }

    @Test
    void everyoneAtTheTableMayEnterTheResultIncludingStandIns() {
        Match match = onTable();

        assertThat(match.canEnterResult(a1)).isTrue();
        assertThat(match.canEnterResult(a2)).isTrue();
        assertThat(match.canEnterResult(b1)).isTrue();
        assertThat(match.canEnterResult(standInOfB)).isTrue();
        assertThat(match.canEnterResult(outsider)).isFalse();
        assertThat(match.canRespondToResult(b1)).as("noch nichts zu bestätigen").isFalse();
        assertThat(match.canBeDecidedByAdmin()).isTrue();
    }

    @Test
    void onlyTheOpposingSideMayConfirmOrReject() {
        Match match = onTable();
        match.submitResult(a1, new MatchResult(10, 4));

        assertThat(match.canEnterResult(a1)).as("Ergebnis steht, nicht erneut eintragen").isFalse();
        assertThat(match.canRespondToResult(a1)).as("Eintragender").isFalse();
        assertThat(match.canRespondToResult(a2)).as("Partner des Eintragenden").isFalse();
        assertThat(match.canRespondToResult(b1)).isTrue();
        assertThat(match.canRespondToResult(standInOfB)).isTrue();
        assertThat(match.canRespondToResult(outsider)).isFalse();
        assertThat(match.canBeDecidedByAdmin()).isTrue();
    }

    @Test
    void enteredBySideBMeansSideAMayConfirm() {
        Match match = onTable();
        match.submitResult(standInOfB, new MatchResult(3, 10));

        assertThat(match.canRespondToResult(a1)).isTrue();
        assertThat(match.canRespondToResult(b1)).isFalse();
    }

    @Test
    void disputedAndConfirmedMatchesNeedAnAdminNotThePlayers() {
        Match disputed = onTable();
        disputed.submitResult(a1, new MatchResult(10, 4));
        disputed.reject(b1);
        assertThat(disputed.canRespondToResult(b1)).isFalse();
        assertThat(disputed.canEnterResult(a1)).isFalse();
        assertThat(disputed.canBeDecidedByAdmin()).isTrue();

        Match confirmed = onTable();
        confirmed.submitResult(a1, new MatchResult(10, 4));
        confirmed.confirm(b1);
        assertThat(confirmed.canRespondToResult(b1)).isFalse();
        assertThat(confirmed.canEnterResult(a1)).isFalse();
        assertThat(confirmed.canBeDecidedByAdmin()).as("Admin darf nachträglich korrigieren").isTrue();
    }
}
