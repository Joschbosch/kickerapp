package zur.koeln.kickertool.local;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.ParticipationUseCase;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.application.port.in.TournamentManagementUseCase;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.local.MockUsers.MockUser;

/**
 * Legt beim Start ein Demo-Turnier an und meldet alle Testspieler an, damit man direkt losspielen kann. Die
 * Datenbank liegt im Speicher, nach jedem Neustart ist alles wieder frisch. Abschaltbar mit
 * {@code kickertool.local.demo-tournament=false}.
 */
@Component
@Profile("local")
class LocalDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoData.class);

    private final PlayerUseCase players;
    private final TournamentManagementUseCase management;
    private final ParticipationUseCase participation;
    private final boolean enabled;
    private final int port;

    LocalDemoData(PlayerUseCase players, TournamentManagementUseCase management, ParticipationUseCase participation,
            @Value("${kickertool.local.demo-tournament:true}") boolean enabled,
            @Value("${server.port:8080}") int port) {
        this.players = players;
        this.management = management;
        this.participation = participation;
        this.enabled = enabled;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Actor admin = actorFor(MockUsers.admin());
        TournamentView tournament = management.plan(admin, "Demo-Turnier", LocalDate.now(),
                new TournamentConfig(3, 3, 1, 0, 10, 5, 4, 2));
        for (MockUser user : MockUsers.players()) {
            Actor actor = actorFor(user);
            participation.register(actor, tournament.tournament().id(), actor.playerId());
        }
        log.info("""

                Demo-Turnier angelegt: {} ({} Spieler angemeldet, noch nicht gestartet)
                Swagger UI:  http://localhost:{}/swagger-ui.html
                Anmelden:    Authorize > oidc (password), Benutzer = Passwort, z. B. admin / admin
                Spieler:     anna, ben, clara, david, emma, felix, greta, hans, ida, jonas
                Admin:       admin""", tournament.tournament().id(), MockUsers.players().size(), port);
    }

    private Actor actorFor(MockUser user) {
        Player player = players.provision(user.subject(), user.displayName());
        return new Actor(player.id(), user.admin());
    }
}
