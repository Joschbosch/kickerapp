package zur.koeln.kickertool.adapter.out.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentRepository;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = PersistenceConfiguration.class)
@EnableJpaRepositories(basePackageClasses = PersistenceConfiguration.class)
public class PersistenceConfiguration {

    @Bean
    PlayerRepository playerRepository(PlayerJpaRepository players) {
        return new JpaPlayerRepository(players);
    }

    @Bean
    TournamentRepository tournamentRepository(TournamentJpaRepository tournaments,
            ParticipantJpaRepository participants, MatchJpaRepository matches) {
        return new JpaTournamentRepository(tournaments, participants, matches);
    }
}
