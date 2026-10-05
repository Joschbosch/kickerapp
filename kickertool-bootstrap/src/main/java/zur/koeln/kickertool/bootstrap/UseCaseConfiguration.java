package zur.koeln.kickertool.bootstrap;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import zur.koeln.kickertool.application.port.in.MatchResultUseCase;
import zur.koeln.kickertool.application.port.in.ParticipationUseCase;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.application.port.in.TournamentEventUseCase;
import zur.koeln.kickertool.application.port.in.TournamentManagementUseCase;
import zur.koeln.kickertool.application.port.in.TournamentQueries;
import zur.koeln.kickertool.application.port.out.PlayerRepository;
import zur.koeln.kickertool.application.port.out.TournamentEventBus;
import zur.koeln.kickertool.application.port.out.TournamentRepository;
import zur.koeln.kickertool.application.service.MatchResultService;
import zur.koeln.kickertool.application.service.ParticipationService;
import zur.koeln.kickertool.application.service.PlayerService;
import zur.koeln.kickertool.application.service.TournamentEventService;
import zur.koeln.kickertool.application.service.TournamentManagementService;
import zur.koeln.kickertool.application.service.TournamentQueryService;
import zur.koeln.kickertool.domain.tournament.NearestRankStandInSuggester;
import zur.koeln.kickertool.domain.tournament.SwissDypTeamAssignmentStrategy;
import zur.koeln.kickertool.domain.tournament.StandInSuggester;
import zur.koeln.kickertool.domain.tournament.TeamAssignmentStrategy;

/**
 * Verdrahtet Domäne und Anwendungsschicht. Die Anwendungsdienste kennen Spring nicht, hier werden sie zu Beans.
 * Die Teamzuteilung ist austauschbar: eine andere Implementierung von {@link TeamAssignmentStrategy} als Bean
 * ersetzt {@link SwissDypTeamAssignmentStrategy}.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TeamAssignmentStrategy teamAssignmentStrategy() {
        return new SwissDypTeamAssignmentStrategy();
    }

    @Bean
    StandInSuggester standInSuggester() {
        return new NearestRankStandInSuggester();
    }

    @Bean
    PlayerUseCase playerUseCase(PlayerRepository players) {
        return new PlayerService(players);
    }

    @Bean
    TournamentManagementUseCase tournamentManagementUseCase(TournamentRepository tournaments,
            PlayerRepository players, TournamentEventBus eventBus, Clock clock, TeamAssignmentStrategy strategy,
            StandInSuggester standInSuggester) {
        return new TournamentManagementService(tournaments, players, eventBus, clock, strategy, standInSuggester);
    }

    @Bean
    ParticipationUseCase participationUseCase(TournamentRepository tournaments, PlayerRepository players,
            TournamentEventBus eventBus, Clock clock) {
        return new ParticipationService(tournaments, players, eventBus, clock);
    }

    @Bean
    MatchResultUseCase matchResultUseCase(TournamentRepository tournaments, PlayerRepository players,
            TournamentEventBus eventBus, Clock clock, StandInSuggester standInSuggester) {
        return new MatchResultService(tournaments, players, eventBus, clock, standInSuggester);
    }

    @Bean
    TournamentQueries tournamentQueries(TournamentRepository tournaments, PlayerRepository players) {
        return new TournamentQueryService(tournaments, players);
    }

    @Bean
    TournamentEventUseCase tournamentEventUseCase(TournamentRepository tournaments, TournamentEventBus eventBus) {
        return new TournamentEventService(tournaments, eventBus);
    }
}
