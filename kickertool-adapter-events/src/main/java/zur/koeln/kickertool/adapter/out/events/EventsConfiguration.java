package zur.koeln.kickertool.adapter.out.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import zur.koeln.kickertool.application.port.out.TournamentEventBus;

@Configuration(proxyBeanMethods = false)
public class EventsConfiguration {

    @Bean
    TournamentEventBus tournamentEventBus() {
        return new InMemoryTournamentEventBus();
    }
}
