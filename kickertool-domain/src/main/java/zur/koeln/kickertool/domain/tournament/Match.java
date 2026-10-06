package zur.koeln.kickertool.domain.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import zur.koeln.kickertool.domain.NotPermittedException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Ein Match zwischen zwei Teams innerhalb einer Runde. Teil des {@link Tournament}-Aggregats, Änderungen
 * laufen ausschließlich über das Turnier.
 */
public final class Match {

    private final MatchId id;
    private final int roundNumber;
    private final int position;
    private Team teamA;
    private Team teamB;
    private MatchStatus status;
    private Integer tableNumber;
    private MatchResult result;
    private PlayerId resultEnteredBy;
    private Side resultEnteredSide;
    private ResultSource resultSource;

    private Match(MatchId id, int roundNumber, int position, Team teamA, Team teamB, MatchStatus status,
            Integer tableNumber, MatchResult result, PlayerId resultEnteredBy, Side resultEnteredSide,
            ResultSource resultSource) {
        this.id = Objects.requireNonNull(id, "id");
        this.roundNumber = roundNumber;
        this.position = position;
        this.teamA = Objects.requireNonNull(teamA, "teamA");
        this.teamB = Objects.requireNonNull(teamB, "teamB");
        this.status = Objects.requireNonNull(status, "status");
        this.tableNumber = tableNumber;
        this.result = result;
        this.resultEnteredBy = resultEnteredBy;
        this.resultEnteredSide = resultEnteredSide;
        this.resultSource = resultSource;
    }

    static Match queued(MatchId id, int roundNumber, int position, Team teamA, Team teamB) {
        return new Match(id, roundNumber, position, teamA, teamB, MatchStatus.QUEUED, null, null, null, null, null);
    }

    /** Rekonstruktion aus der Persistenz. */
    public static Match restore(MatchId id, int roundNumber, int position, Team teamA, Team teamB,
            MatchStatus status, Integer tableNumber, MatchResult result, PlayerId resultEnteredBy,
            Side resultEnteredSide, ResultSource resultSource) {
        return new Match(id, roundNumber, position, teamA, teamB, status, tableNumber, result, resultEnteredBy,
                resultEnteredSide, resultSource);
    }

    /** Das Match bekommt einen Tisch. Dummy-Plätze werden dabei mit dem vorgeschlagenen Einspringer besetzt. */
    void placeOnTable(int table, UnaryOperator<DummySlot> standIns) {
        requireStatus(MatchStatus.QUEUED, "Nur wartende Matches können an einen Tisch gesetzt werden");
        this.teamA = teamA.withDummies(standIns);
        this.teamB = teamB.withDummies(standIns);
        this.tableNumber = table;
        this.status = MatchStatus.ON_TABLE;
    }

    /** Ein Team trägt ein Ergebnis ein. Das Gegnerteam muss es bestätigen. */
    void submitResult(PlayerId by, MatchResult proposed) {
        requireStatus(MatchStatus.ON_TABLE, "Ein Ergebnis kann nur für ein Match am Tisch eingetragen werden");
        Side side = requireSideOf(by);
        this.result = Objects.requireNonNull(proposed, "proposed");
        this.resultEnteredBy = by;
        this.resultEnteredSide = side;
        this.resultSource = null;
        this.status = MatchStatus.RESULT_ENTERED;
    }

    void confirm(PlayerId by) {
        requireStatus(MatchStatus.RESULT_ENTERED, "Es gibt kein Ergebnis zu bestätigen");
        requireOpponentOfEnteringSide(by);
        this.resultSource = ResultSource.TEAM;
        this.status = MatchStatus.CONFIRMED;
    }

    void reject(PlayerId by) {
        requireStatus(MatchStatus.RESULT_ENTERED, "Es gibt kein Ergebnis abzulehnen");
        requireOpponentOfEnteringSide(by);
        this.status = MatchStatus.DISPUTED;
    }

    /** Der Admin legt das Ergebnis fest oder korrigiert es (auch nachträglich). */
    void decide(MatchResult decided) {
        if (status == MatchStatus.QUEUED) {
            throw new RuleViolationException("Für ein noch nicht gespieltes Match kann kein Ergebnis gesetzt werden");
        }
        this.result = Objects.requireNonNull(decided, "decided");
        this.resultSource = ResultSource.ADMIN;
        this.status = MatchStatus.CONFIRMED;
    }

    public MatchId id() {
        return id;
    }

    public int roundNumber() {
        return roundNumber;
    }

    /** Position in der Warteschlange der Runde, beginnend bei 1. */
    public int position() {
        return position;
    }

    public Team teamA() {
        return teamA;
    }

    public Team teamB() {
        return teamB;
    }

    public Team team(Side side) {
        return side == Side.A ? teamA : teamB;
    }

    public MatchStatus status() {
        return status;
    }

    public Optional<Integer> tableNumber() {
        return Optional.ofNullable(tableNumber);
    }

    /** Eingetragenes (ggf. noch unbestätigtes) oder festgelegtes Ergebnis. */
    public Optional<MatchResult> result() {
        return Optional.ofNullable(result);
    }

    public Optional<PlayerId> resultEnteredBy() {
        return Optional.ofNullable(resultEnteredBy);
    }

    public Optional<Side> resultEnteredSide() {
        return Optional.ofNullable(resultEnteredSide);
    }

    public Optional<ResultSource> resultSource() {
        return Optional.ofNullable(resultSource);
    }

    public boolean isConfirmed() {
        return status == MatchStatus.CONFIRMED;
    }

    /** Das Match ist gespielt, ein Ergebnis wurde eingetragen oder festgelegt. Der Tisch ist wieder frei. */
    public boolean isPlayed() {
        return status == MatchStatus.RESULT_ENTERED || status == MatchStatus.DISPUTED
                || status == MatchStatus.CONFIRMED;
    }

    /** Alle echten Spieler beider Teams. */
    public List<PlayerId> realPlayers() {
        List<PlayerId> all = new ArrayList<>(teamA.realPlayers());
        all.addAll(teamB.realPlayers());
        return List.copyOf(all);
    }

    /** Die Seite, auf der die Person am Tisch steht (echter Spieler oder Einspringer), sonst leer. */
    public Optional<Side> sideOf(PlayerId player) {
        if (teamA.actingPlayers().contains(player)) {
            return Optional.of(Side.A);
        }
        if (teamB.actingPlayers().contains(player)) {
            return Optional.of(Side.B);
        }
        return Optional.empty();
    }

    /** Darf der Spieler jetzt ein Ergebnis eintragen? Er muss am Tisch stehen, das Match muss gerade spielen. */
    public boolean canEnterResult(PlayerId player) {
        return status == MatchStatus.ON_TABLE && sideOf(player).isPresent();
    }

    /** Darf der Spieler das eingetragene Ergebnis bestätigen oder ablehnen? Nur das Gegnerteam des Eintragenden. */
    public boolean canRespondToResult(PlayerId player) {
        return status == MatchStatus.RESULT_ENTERED
                && sideOf(player).filter(side -> side != resultEnteredSide).isPresent();
    }

    /** Kann ein Admin ein Ergebnis festlegen oder korrigieren? Ab dem Moment, in dem das Match spielt. */
    public boolean canBeDecidedByAdmin() {
        return status != MatchStatus.QUEUED;
    }

    private Side requireSideOf(PlayerId player) {
        return sideOf(player).orElseThrow(
                () -> new NotPermittedException("Der Spieler gehört nicht zu diesem Match"));
    }

    private void requireOpponentOfEnteringSide(PlayerId by) {
        Side side = requireSideOf(by);
        if (side == resultEnteredSide) {
            throw new NotPermittedException("Das Ergebnis muss vom Gegnerteam bestätigt oder abgelehnt werden");
        }
    }

    private void requireStatus(MatchStatus expected, String message) {
        if (status != expected) {
            throw new RuleViolationException(message + " (Status: " + status + ")");
        }
    }
}
