package zur.koeln.kickertool.domain.statistics;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Ranking;
import zur.koeln.kickertool.domain.tournament.RankingEntry;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/**
 * Die Statistik eines Spielers über alle Turniere: die Summen und der Stand in jedem einzelnen Turnier.
 *
 * <p>Gezählt werden laufende und beendete Turniere, nicht aber geplante: Dort wurde noch nicht gespielt, alle
 * stehen auf Rang 1. Die Werte kommen aus der Rangliste des jeweiligen Turniers, nur bestätigte Ergebnisse zählen,
 * Spiele als Einspringer nicht.
 *
 * @param titles  beendete Turniere, in denen der Spieler gespielt hat und Rang 1 belegt (auch geteilt)
 * @param podiums beendete Turniere, in denen der Spieler gespielt hat und Rang 1 bis 3 belegt (auch geteilt)
 */
public record PlayerStatistics(PlayerId playerId, Totals totals, List<TournamentStanding> tournaments) {

    private static final int PODIUM_RANKS = 3;

    public PlayerStatistics {
        tournaments = List.copyOf(tournaments);
    }

    /** Die Summen über alle Turniere des Spielers. */
    public record Totals(int tournamentCount, int finishedTournamentCount, int titles, int podiums, int matchesPlayed,
            int wins, int draws, int losses, int goalsFor, int goalsAgainst, int points) {

        public int goalDifference() {
            return goalsFor - goalsAgainst;
        }
    }

    /** Baut die Statistik aus den Turnieren, an denen der Spieler teilnimmt. Neueste Turniere zuerst. */
    public static PlayerStatistics of(PlayerId playerId, Collection<Tournament> tournaments) {
        List<TournamentStanding> standings = tournaments.stream()
                .filter(t -> t.status() != TournamentStatus.PLANNED)
                .map(t -> standing(playerId, t))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(TournamentStanding::date).reversed()
                        .thenComparing(TournamentStanding::name))
                .toList();
        return new PlayerStatistics(playerId, totals(standings), standings);
    }

    private static Optional<TournamentStanding> standing(PlayerId playerId, Tournament tournament) {
        Ranking ranking = tournament.ranking();
        return ranking.entryOf(playerId).map(entry -> toStanding(tournament, ranking.entries().size(), entry));
    }

    private static TournamentStanding toStanding(Tournament tournament, int participantCount, RankingEntry entry) {
        return new TournamentStanding(tournament.id(), tournament.name(), tournament.date(), tournament.status(),
                entry.status(), entry.rank(), participantCount, entry.matchesPlayed(), entry.wins(), entry.draws(),
                entry.losses(), entry.goalsFor(), entry.goalsAgainst(), entry.points());
    }

    private static Totals totals(List<TournamentStanding> standings) {
        int finished = 0;
        int titles = 0;
        int podiums = 0;
        int matches = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int points = 0;
        for (TournamentStanding s : standings) {
            if (s.tournamentStatus() == TournamentStatus.FINISHED) {
                finished++;
                if (s.matchesPlayed() > 0 && s.rank() == 1) {
                    titles++;
                }
                if (s.matchesPlayed() > 0 && s.rank() <= PODIUM_RANKS) {
                    podiums++;
                }
            }
            matches += s.matchesPlayed();
            wins += s.wins();
            draws += s.draws();
            losses += s.losses();
            goalsFor += s.goalsFor();
            goalsAgainst += s.goalsAgainst();
            points += s.points();
        }
        return new Totals(standings.size(), finished, titles, podiums, matches, wins, draws, losses, goalsFor,
                goalsAgainst, points);
    }
}
