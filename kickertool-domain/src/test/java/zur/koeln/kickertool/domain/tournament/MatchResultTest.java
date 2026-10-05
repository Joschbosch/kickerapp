package zur.koeln.kickertool.domain.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MatchResultTest {

    @Test
    void determinesWinner() {
        assertThat(new MatchResult(10, 3).winner()).contains(Side.A);
        assertThat(new MatchResult(2, 6).winner()).contains(Side.B);
        assertThat(new MatchResult(4, 4).winner()).isEmpty();
    }

    @Test
    void rejectsNegativeGoals() {
        assertThatThrownBy(() -> new MatchResult(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesGoalLimit() {
        new MatchResult(10, 9).validateAgainst(10);
        new MatchResult(5, 5).validateAgainst(10);
        assertThatThrownBy(() -> new MatchResult(11, 0).validateAgainst(10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResult(10, 10).validateAgainst(10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
