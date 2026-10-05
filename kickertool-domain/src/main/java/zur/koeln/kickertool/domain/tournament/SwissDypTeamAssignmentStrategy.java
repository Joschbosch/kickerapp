package zur.koeln.kickertool.domain.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import zur.koeln.kickertool.domain.player.PlayerId;

/**
 * Teamzuteilung im Stil eines Schweizer DYP (Draw Your Partner).
 *
 * <ul>
 *   <li><b>Zufallsrunden:</b> Die ersten {@code randomRounds} Runden werden komplett zufällig ausgelost, die
 *       Rangliste spielt keine Rolle.</li>
 *   <li><b>Danach:</b> Die Spieler werden nach Rang in Blöcke zu je 4 aufgeteilt (Rang 1 bis 4, 5 bis 8, ...).
 *       Spieler mit gleichem Rang werden vorher gemischt, damit die Blöcke nicht von der Anmeldereihenfolge
 *       abhängen. Innerhalb eines Blocks wird gemischt und in zwei Teams geteilt. Die besten Blöcke spielen in
 *       der Auslosung zuerst.</li>
 *   <li><b>Partnerregel (in allen Runden):</b> Wer in der letzten Runde Partner war, soll es nicht gleich wieder
 *       sein. Das ist ein sehr starker Wunsch: Aus den möglichen Aufteilungen eines Blocks wird eine ohne
 *       Wiederholung bevorzugt, nur wenn keine existiert, wird eine Wiederholung in Kauf genommen.</li>
 *   <li><b>Dummys mit Rotation:</b> Fehlen Spieler zur vollen Vierergruppe, bildet ein unvollständiger Block die
 *       Lücke und wird mit Dummys aufgefüllt. Dafür werden die Spieler gewählt, die bisher am seltensten in einem
 *       Match mit Dummys standen. Bei Gleichstand trifft es die niedrigsten Ränge (in Zufallsrunden wird
 *       zufällig gewählt). So trifft es nicht immer dieselben Spieler. Der Block mit den Dummys kommt in der
 *       Auslosung zuletzt dran.</li>
 * </ul>
 */
public class SwissDypTeamAssignmentStrategy implements TeamAssignmentStrategy {

    private static final int BLOCK_SIZE = 4;

    private final Random random;

    public SwissDypTeamAssignmentStrategy() {
        this(new Random());
    }

    public SwissDypTeamAssignmentStrategy(Random random) {
        this.random = random;
    }

    @Override
    public List<Pairing> assign(AssignmentContext context) {
        boolean randomRound = context.roundNumber() <= context.config().randomRounds();
        List<PlayerId> ordered = randomRound ? shuffled(context.players()) : byRankWithShuffledTies(context);
        Set<Set<PlayerId>> lastPartners = partnersOfLastRound(context.previousRounds());

        List<PlayerId> dummyBlock = chooseDummyBlock(ordered, context.previousRounds());
        List<PlayerId> fullBlocks = new ArrayList<>(ordered);
        fullBlocks.removeAll(dummyBlock);

        List<Pairing> pairings = new ArrayList<>();
        for (int start = 0; start < fullBlocks.size(); start += BLOCK_SIZE) {
            pairings.add(pair(fullBlocks.subList(start, start + BLOCK_SIZE), lastPartners));
        }
        if (!dummyBlock.isEmpty()) {
            pairings.add(pair(dummyBlock, lastPartners));
        }
        return pairings;
    }

    // ---------------------------------------------------------------- Dummys

    /**
     * Wählt die Spieler für den unvollständigen Block (leer, wenn alle Vierergruppen voll werden). Gewählt werden
     * die mit den wenigsten Matches gegen oder mit Dummys, bei Gleichstand die zuletzt in der Reihenfolge, also die
     * niedrigsten Ränge.
     */
    private List<PlayerId> chooseDummyBlock(List<PlayerId> ordered, List<Round> previousRounds) {
        int missing = ordered.size() % BLOCK_SIZE;
        if (missing == 0) {
            return List.of();
        }
        Map<PlayerId, Integer> dummyMatches = dummyMatchesPerPlayer(previousRounds);
        List<PlayerId> candidates = new ArrayList<>(ordered);
        Collections.reverse(candidates);
        candidates.sort(Comparator.comparingInt(player -> dummyMatches.getOrDefault(player, 0)));
        return List.copyOf(candidates.subList(0, missing));
    }

    /** In wie vielen früheren Runden stand der Spieler in einem Match mit Dummys. Einspringer zählen nicht. */
    private static Map<PlayerId, Integer> dummyMatchesPerPlayer(List<Round> previousRounds) {
        Map<PlayerId, Integer> counts = new HashMap<>();
        for (Round round : previousRounds) {
            for (Match match : round.matches()) {
                if (match.teamA().dummyCount() + match.teamB().dummyCount() > 0) {
                    match.realPlayers().forEach(player -> counts.merge(player, 1, Integer::sum));
                }
            }
        }
        return counts;
    }

    // ---------------------------------------------------------------- Reihenfolge

    private List<PlayerId> shuffled(List<PlayerId> players) {
        List<PlayerId> copy = new ArrayList<>(players);
        Collections.shuffle(copy, random);
        return copy;
    }

    /** Sortiert nach Rang. Wer den gleichen Rang hat, wird gemischt. */
    private List<PlayerId> byRankWithShuffledTies(AssignmentContext context) {
        Ranking ranking = context.ranking();
        List<PlayerId> sorted = new ArrayList<>(context.players());
        sorted.sort(Comparator.comparingInt(player -> ranking.rankOf(player).orElse(Integer.MAX_VALUE)));

        List<PlayerId> result = new ArrayList<>(sorted.size());
        int groupStart = 0;
        for (int i = 1; i <= sorted.size(); i++) {
            boolean groupEnds = i == sorted.size()
                    || !ranking.rankOf(sorted.get(i)).equals(ranking.rankOf(sorted.get(groupStart)));
            if (groupEnds) {
                List<PlayerId> group = new ArrayList<>(sorted.subList(groupStart, i));
                Collections.shuffle(group, random);
                result.addAll(group);
                groupStart = i;
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- Blöcke

    private Pairing pair(List<PlayerId> block, Set<Set<PlayerId>> lastPartners) {
        List<PlayerId> mixed = shuffled(block);
        return switch (mixed.size()) {
            case 4 -> pairFour(mixed, lastPartners);
            case 3 -> pairThree(mixed, lastPartners);
            case 2 -> new Pairing(
                    new Team(player(mixed.get(0)), DummySlot.unassigned()),
                    new Team(player(mixed.get(1)), DummySlot.unassigned()));
            default -> new Pairing(
                    new Team(player(mixed.get(0)), DummySlot.unassigned()),
                    new Team(DummySlot.unassigned(), DummySlot.unassigned()));
        };
    }

    /** Von den drei möglichen Aufteilungen: eine ohne Partner-Wiederholung, sonst die mit den wenigsten. */
    private Pairing pairFour(List<PlayerId> p, Set<Set<PlayerId>> lastPartners) {
        List<Pairing> options = new ArrayList<>(List.of(
                new Pairing(new Team(player(p.get(0)), player(p.get(1))), new Team(player(p.get(2)), player(p.get(3)))),
                new Pairing(new Team(player(p.get(0)), player(p.get(2))), new Team(player(p.get(1)), player(p.get(3)))),
                new Pairing(new Team(player(p.get(0)), player(p.get(3))), new Team(player(p.get(1)), player(p.get(2))))));
        return leastRepeats(options, lastPartners);
    }

    /** Zwei Spieler bilden ein Team, der dritte spielt mit einem Dummy. Wer mit dem Dummy spielt, ist offen. */
    private Pairing pairThree(List<PlayerId> p, Set<Set<PlayerId>> lastPartners) {
        List<Pairing> options = new ArrayList<>();
        for (int withDummy = 0; withDummy < 3; withDummy++) {
            List<PlayerId> others = new ArrayList<>(p);
            PlayerId single = others.remove(withDummy);
            Team real = new Team(player(others.get(0)), player(others.get(1)));
            Team mixedTeam = new Team(player(single), DummySlot.unassigned());
            options.add(random.nextBoolean() ? new Pairing(real, mixedTeam) : new Pairing(mixedTeam, real));
        }
        return leastRepeats(options, lastPartners);
    }

    private Pairing leastRepeats(List<Pairing> options, Set<Set<PlayerId>> lastPartners) {
        Collections.shuffle(options, random);
        return options.stream()
                .min(Comparator.comparingInt(option -> repeats(option, lastPartners)))
                .orElseThrow();
    }

    // ---------------------------------------------------------------- Partnerregel

    private static int repeats(Pairing pairing, Set<Set<PlayerId>> lastPartners) {
        int repeats = 0;
        for (Team team : List.of(pairing.teamA(), pairing.teamB())) {
            if (team.first() instanceof PlayerSlot a && team.second() instanceof PlayerSlot b
                    && lastPartners.contains(Set.of(a.playerId(), b.playerId()))) {
                repeats++;
            }
        }
        return repeats;
    }

    /** Alle Paare echter Spieler, die in der letzten Runde zusammen ein Team waren. */
    private static Set<Set<PlayerId>> partnersOfLastRound(List<Round> previousRounds) {
        Set<Set<PlayerId>> partners = new HashSet<>();
        if (previousRounds.isEmpty()) {
            return partners;
        }
        Round last = previousRounds.get(previousRounds.size() - 1);
        for (Match match : last.matches()) {
            for (Team team : List.of(match.teamA(), match.teamB())) {
                if (team.first() instanceof PlayerSlot a && team.second() instanceof PlayerSlot b) {
                    partners.add(Set.of(a.playerId(), b.playerId()));
                }
            }
        }
        return partners;
    }

    private static PlayerSlot player(PlayerId id) {
        return new PlayerSlot(id);
    }
}
