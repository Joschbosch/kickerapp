package zur.koeln.kickertool.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.SwissDypTeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Prüft Flyway-Migration und JPA-Mapping gegen ein echtes PostgreSQL. Wird übersprungen, wenn kein Docker
 * verfügbar ist. Die übrigen Persistenz-Tests laufen gegen H2.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9/realms/test")
class PostgresPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    TournamentRepository tournaments;

    @Autowired
    PlayerRepository players;

    @Test
    void savesAndLoadsATournamentWithRoundsAndDummies() {
        NearestRankStandInSuggester suggester = new NearestRankStandInSuggester();
        Tournament tournament = Tournament.plan(TournamentId.random(), "Postgres-Turnier", LocalDate.of(2026, 10, 5),
                new TournamentConfig(2, 3, 1, 0, 10, 5, null));
        for (int i = 1; i <= 10; i++) {
            Player player = players.save(new Player(PlayerId.random(), "pg-sub-" + i, "Spieler " + i));
            tournament.register(player.id(), Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i));
        }
        tournament.start();
        tournament.startNextRound(new SwissDypTeamAssignmentStrategy(new Random(5)), suggester);
        Match first = tournament.rounds().get(0).matches().get(0);
        tournament.submitResult(first.id(), first.teamA().actingPlayers().get(0), new MatchResult(10, 3), suggester);
        tournament.confirmResult(first.id(), first.teamB().actingPlayers().get(0));

        tournaments.save(tournament);

        Tournament loaded = tournaments.findByIdForUpdate(tournament.id()).orElseThrow();
        assertThat(loaded.participants()).hasSize(10);
        assertThat(loaded.rounds()).hasSize(1);
        assertThat(loaded.rounds().get(0).matches()).hasSize(3);
        assertThat(loaded.ranking()).isEqualTo(tournament.ranking());
        assertThat(loaded.rounds().get(0).matches().get(2).teamA()).isEqualTo(
                tournament.rounds().get(0).matches().get(2).teamA());
        assertThat(tournaments.findAllSummaries()).extracting(s -> s.name()).contains("Postgres-Turnier");
        assertThat(players.findAll()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(List.copyOf(loaded.participants())).isNotEmpty();
    }
}
