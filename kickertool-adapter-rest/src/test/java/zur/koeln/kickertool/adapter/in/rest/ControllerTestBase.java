package zur.koeln.kickertool.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import zur.koeln.kickertool.adapter.in.rest.security.SecurityConfiguration;
import zur.koeln.kickertool.application.port.in.MatchResultUseCase;
import zur.koeln.kickertool.application.port.in.ParticipationUseCase;
import zur.koeln.kickertool.application.port.in.PlayerUseCase;
import zur.koeln.kickertool.application.port.in.TournamentEventUseCase;
import zur.koeln.kickertool.application.port.in.TournamentManagementUseCase;
import zur.koeln.kickertool.application.port.in.TournamentQueries;
import zur.koeln.kickertool.domain.player.Player;
import zur.koeln.kickertool.domain.player.PlayerId;

@WebMvcTest
@Import({SecurityConfiguration.class, WebConfiguration.class})
abstract class ControllerTestBase {

    protected static final Player ANNA = new Player(PlayerId.random(), "sub-anna", "Anna");

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected PlayerUseCase players;
    @MockitoBean
    protected TournamentManagementUseCase management;
    @MockitoBean
    protected TournamentQueries queries;
    @MockitoBean
    protected ParticipationUseCase participation;
    @MockitoBean
    protected MatchResultUseCase results;
    @MockitoBean
    protected TournamentEventUseCase events;
    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @BeforeEach
    void provisionAnna() {
        when(players.provision(anyString(), anyString())).thenReturn(ANNA);
        when(players.getPlayer(ANNA.id())).thenReturn(ANNA);
    }

    /** Ein angemeldeter normaler Spieler (entspricht einem gültigen Token ohne Admin-Rolle). */
    protected static RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(ANNA.subject()).claim("name", ANNA.displayName()));
    }

    /** Ein angemeldeter Admin (so, wie der Token-Konverter ihn aus dem Rollen-Claim erzeugt). */
    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(ANNA.subject()).claim("name", ANNA.displayName()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
