package zur.koeln.kickertool.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.SwissDypTeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Ein laufendes Turnier mit 10 Spielern (also einem Dummy-Match) und gestarteter erster Runde. */
record TestData(Tournament tournament, TournamentView view, List<Player> players) {

    static TestData runningTournament() {
        List<Player> players = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new Player(PlayerId.random(), "sub-" + i, "Spieler " + i))
                .toList();
        Tournament tournament = Tournament.plan(TournamentId.random(), "Herbstturnier", LocalDate.of(2026, 10, 5),
                new TournamentConfig(2, 3, 1, 0, 10, 5, null));
        for (int i = 0; i < players.size(); i++) {
            tournament.register(players.get(i).id(), Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i));
        }
        tournament.start();
        tournament.startNextRound(new SwissDypTeamAssignmentStrategy(new Random(3)), new NearestRankStandInSuggester());

        Map<PlayerId, Player> byId = new HashMap<>();
        players.forEach(p -> byId.put(p.id(), p));
        return new TestData(tournament, new TournamentView(tournament, byId), players);
    }
}
