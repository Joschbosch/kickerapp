package zur.koeln.kickertool.domain.tournament;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.RuleViolationException;
import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Aggregatwurzel eines Turniers: Konfiguration, Teilnehmer, Runden und Matches.
 *
 * <p>Ablauf: Das Turnier wird geplant (Anmeldung offen), gestartet, und der Admin startet Runde für Runde.
 * In jeder Runde spielt jeder aktive Spieler genau ein Match. Die Matches einer Runde kommen in Wellen an die
 * Tische: Die erste Welle besteht aus den ersten {@code tableCount} Matches. Sobald für alle Matches am Tisch
 * ein Ergebnis eingetragen ist, rückt die nächste Welle nach. Eine neue Runde kann erst starten, wenn alle
 * Ergebnisse der letzten Runde bestätigt sind.
 */
public final class Tournament {

    /** Höchstzahl an Dummys pro Runde. */
    public static final int MAX_DUMMIES = 3;

    private final TournamentId id;
    private String name;
    private LocalDate date;
    private TournamentStatus status;
    private TournamentConfig config;
    private final Map<PlayerId, Participant> participants = new LinkedHashMap<>();
    private final List<Round> rounds = new ArrayList<>();

    private Tournament(TournamentId id, String name, LocalDate date, TournamentStatus status,
            TournamentConfig config) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireName(name);
        this.date = Objects.requireNonNull(date, "date");
        this.status = Objects.requireNonNull(status, "status");
        this.config = Objects.requireNonNull(config, "config");
    }

    public static Tournament plan(TournamentId id, String name, LocalDate date, TournamentConfig config) {
        return new Tournament(id, name, date, TournamentStatus.PLANNED, config);
    }

    /** Rekonstruktion aus der Persistenz. */
    public static Tournament restore(TournamentId id, String name, LocalDate date, TournamentStatus status,
            TournamentConfig config, Collection<Participant> participants, Collection<Round> rounds) {
        Tournament tournament = new Tournament(id, name, date, status, config);
        participants.forEach(p -> tournament.participants.put(p.playerId(), p));
        tournament.rounds.addAll(rounds.stream().sorted(Comparator.comparingInt(Round::number)).toList());
        return tournament;
    }

    // ---------------------------------------------------------------- Planung und Anmeldung

    public void updateDetails(String newName, LocalDate newDate) {
        requireNotFinished();
        this.name = requireName(newName);
        this.date = Objects.requireNonNull(newDate, "date");
    }

    /** Die Konfiguration kann jederzeit geändert werden. Eine neue Tischzahl gilt ab der nächsten Welle. */
    public void updateConfig(TournamentConfig newConfig) {
        requireNotFinished();
        this.config = Objects.requireNonNull(newConfig, "config");
    }

    /** Anmeldung ist bis zum Ende des Turniers möglich. Wer später kommt, spielt ab der nächsten Runde mit. */
    public void register(PlayerId playerId, Instant now) {
        requireNotFinished();
        if (participants.containsKey(playerId)) {
            throw new RuleViolationException("Der Spieler ist bereits angemeldet");
        }
        participants.put(playerId, Participant.register(playerId, now));
    }

    /** Abmelden ist nur vor dem Start möglich. Danach gibt es nur noch Pause oder Ausscheiden. */
    public void unregister(PlayerId playerId) {
        requireStatus(TournamentStatus.PLANNED, "Abmelden ist nur vor dem Start möglich");
        if (participants.remove(playerId) == null) {
            throw new NotFoundException("Der Spieler ist nicht angemeldet");
        }
    }

    /** Pausiert ab der nächsten Runde. Die aktuelle Runde wird normal zu Ende gespielt. */
    public void pause(PlayerId playerId) {
        requireStatus(TournamentStatus.RUNNING, "Pausieren ist nur während des laufenden Turniers möglich");
        participant(playerId).pause();
    }

    public void resume(PlayerId playerId) {
        requireStatus(TournamentStatus.RUNNING, "Wiedereinsteigen ist nur während des laufenden Turniers möglich");
        participant(playerId).resume();
    }

    /** Scheidet ab der nächsten Runde aus. Die bisherigen Punkte bleiben in der Rangliste erhalten. */
    public void withdraw(PlayerId playerId) {
        requireStatus(TournamentStatus.RUNNING, "Ausscheiden ist nur während des laufenden Turniers möglich");
        participant(playerId).withdraw();
    }

    // ---------------------------------------------------------------- Ablauf

    public void start() {
        requireStatus(TournamentStatus.PLANNED, "Das Turnier wurde bereits gestartet");
        if (activePlayers().isEmpty()) {
            throw new RuleViolationException("Ohne angemeldete Spieler kann das Turnier nicht starten");
        }
        this.status = TournamentStatus.RUNNING;
    }

    /**
     * Startet die nächste Runde: lost die aktiven Spieler zu Teams zusammen und setzt die erste Welle an die
     * Tische.
     */
    public void startNextRound(TeamAssignmentStrategy strategy, StandInSuggester standInSuggester) {
        requireStatus(TournamentStatus.RUNNING, "Runden können nur in einem laufenden Turnier gestartet werden");
        currentRound().filter(r -> !r.isComplete()).ifPresent(r -> {
            throw new RuleViolationException(
                    "Runde " + r.number() + " ist noch nicht abgeschlossen, es fehlen bestätigte Ergebnisse");
        });
        List<PlayerId> active = activePlayers();
        if (active.isEmpty()) {
            throw new RuleViolationException("Es gibt keine aktiven Spieler für die nächste Runde");
        }

        int number = rounds.size() + 1;
        Ranking ranking = ranking();
        List<PlayerId> byRank = active.stream()
                .sorted(Comparator.comparingInt((PlayerId p) -> ranking.rankOf(p).orElse(Integer.MAX_VALUE)))
                .toList();
        List<Pairing> pairings = strategy.assign(
                new AssignmentContext(number, byRank, ranking, List.copyOf(rounds), config));
        validatePairings(pairings, active);

        List<Match> matches = new ArrayList<>(pairings.size());
        for (int i = 0; i < pairings.size(); i++) {
            Pairing pairing = pairings.get(i);
            matches.add(Match.queued(MatchId.random(), number, i + 1, pairing.teamA(), pairing.teamB()));
        }
        rounds.add(Round.start(number, matches));
        advanceWaves(standInSuggester);
    }

    public void finish() {
        requireStatus(TournamentStatus.RUNNING, "Nur ein laufendes Turnier kann beendet werden");
        currentRound().filter(r -> !r.isComplete()).ifPresent(r -> {
            throw new RuleViolationException(
                    "Runde " + r.number() + " ist noch nicht abgeschlossen, es fehlen bestätigte Ergebnisse");
        });
        this.status = TournamentStatus.FINISHED;
    }

    // ---------------------------------------------------------------- Ergebnisse

    /** Ein Team trägt das Ergebnis ein. Danach kann die nächste Welle nachrücken. */
    public void submitResult(MatchId matchId, PlayerId by, MatchResult result, StandInSuggester standInSuggester) {
        requireStatus(TournamentStatus.RUNNING, "Ergebnisse können nur im laufenden Turnier eingetragen werden");
        result.validateAgainst(config.goalLimit());
        requireMatch(matchId).submitResult(by, result);
        advanceWaves(standInSuggester);
    }

    public void confirmResult(MatchId matchId, PlayerId by) {
        requireStatus(TournamentStatus.RUNNING, "Ergebnisse können nur im laufenden Turnier bestätigt werden");
        requireMatch(matchId).confirm(by);
    }

    /** Das Gegnerteam lehnt ab, der Admin muss das Ergebnis festlegen. */
    public void rejectResult(MatchId matchId, PlayerId by) {
        requireStatus(TournamentStatus.RUNNING, "Ergebnisse können nur im laufenden Turnier abgelehnt werden");
        requireMatch(matchId).reject(by);
    }

    /**
     * Der Admin legt ein Ergebnis fest oder korrigiert ein bestätigtes, auch nach Turnierende. Es gilt sofort
     * als bestätigt.
     */
    public void decideResult(MatchId matchId, MatchResult result, StandInSuggester standInSuggester) {
        result.validateAgainst(config.goalLimit());
        requireMatch(matchId).decide(result);
        advanceWaves(standInSuggester);
    }

    // ---------------------------------------------------------------- Abfragen

    public Ranking ranking() {
        return RankingCalculator.calculate(config, participants.values(), rounds);
    }

    public Optional<Round> currentRound() {
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.get(rounds.size() - 1));
    }

    public Optional<Match> findMatch(MatchId matchId) {
        return rounds.stream().flatMap(r -> r.matches().stream()).filter(m -> m.id().equals(matchId)).findFirst();
    }

    public Optional<Participant> findParticipant(PlayerId playerId) {
        return Optional.ofNullable(participants.get(playerId));
    }

    public TournamentId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public LocalDate date() {
        return date;
    }

    public TournamentStatus status() {
        return status;
    }

    public TournamentConfig config() {
        return config;
    }

    public List<Participant> participants() {
        return List.copyOf(participants.values());
    }

    public List<Round> rounds() {
        return List.copyOf(rounds);
    }

    // ---------------------------------------------------------------- intern

    /**
     * Setzt die nächste Welle an die Tische, wenn kein Match mehr am Tisch spielt. Dummy-Plätze bekommen dabei
     * einen Einspringer: einen aktiven Teilnehmer, der gerade nicht an einem Tisch steht.
     */
    private void advanceWaves(StandInSuggester suggester) {
        Round round = currentRound().orElse(null);
        if (round == null || !round.matchesOnTable().isEmpty()) {
            return;
        }
        List<Match> queued = round.queuedMatches();
        if (queued.isEmpty()) {
            return;
        }
        List<Match> wave = queued.subList(0, Math.min(config.tableCount(), queued.size()));

        Set<PlayerId> unavailable = new HashSet<>();
        wave.forEach(match -> unavailable.addAll(match.realPlayers()));
        Ranking ranking = ranking();

        int table = 1;
        for (Match match : wave) {
            match.placeOnTable(table++, dummy -> {
                List<PlayerId> candidates = activePlayers().stream().filter(p -> !unavailable.contains(p)).toList();
                Optional<PlayerId> standIn = suggester.suggest(match, ranking, candidates);
                standIn.ifPresent(unavailable::add);
                return new DummySlot(standIn.orElse(null));
            });
        }
    }

    private void validatePairings(List<Pairing> pairings, List<PlayerId> active) {
        Set<PlayerId> seen = new HashSet<>();
        int dummies = 0;
        for (Pairing pairing : pairings) {
            for (Team team : List.of(pairing.teamA(), pairing.teamB())) {
                dummies += team.dummyCount();
                for (PlayerId player : team.realPlayers()) {
                    if (!active.contains(player)) {
                        throw new RuleViolationException("Die Auslosung enthält einen nicht aktiven Spieler");
                    }
                    if (!seen.add(player)) {
                        throw new RuleViolationException("Die Auslosung enthält einen Spieler mehrfach");
                    }
                }
            }
        }
        if (seen.size() != active.size()) {
            throw new RuleViolationException("Die Auslosung enthält nicht alle aktiven Spieler");
        }
        if (dummies > MAX_DUMMIES) {
            throw new RuleViolationException("Die Auslosung enthält mehr als " + MAX_DUMMIES + " Dummys");
        }
    }

    private List<PlayerId> activePlayers() {
        return participants.values().stream().filter(Participant::isActive).map(Participant::playerId).toList();
    }

    private Participant participant(PlayerId playerId) {
        return findParticipant(playerId).orElseThrow(
                () -> new NotFoundException("Der Spieler nimmt nicht an diesem Turnier teil"));
    }

    private Match requireMatch(MatchId matchId) {
        return findMatch(matchId).orElseThrow(() -> new NotFoundException("Match nicht gefunden: " + matchId));
    }

    private void requireStatus(TournamentStatus expected, String message) {
        if (status != expected) {
            throw new RuleViolationException(message + " (Turnierstatus: " + status + ")");
        }
    }

    private void requireNotFinished() {
        if (status == TournamentStatus.FINISHED) {
            throw new RuleViolationException("Das Turnier ist bereits beendet");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }
}
