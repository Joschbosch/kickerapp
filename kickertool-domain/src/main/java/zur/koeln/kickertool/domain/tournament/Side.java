package zur.koeln.kickertool.domain.tournament;

/** Eine der beiden Seiten eines Matches. */
public enum Side {
    A,
    B;

    public Side opposite() {
        return this == A ? B : A;
    }
}
