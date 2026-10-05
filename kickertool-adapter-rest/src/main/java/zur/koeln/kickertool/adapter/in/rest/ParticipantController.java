package zur.koeln.kickertool.adapter.in.rest;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.adapter.in.rest.dto.Requests;
import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.ParticipationUseCase;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/**
 * Anmeldung und Teilnahmestatus. Als Spieler-ID kann überall {@code me} stehen. Spieler verwalten nur sich selbst,
 * Admins jeden.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/participants")
@Tag(name = "Teilnahme", description = "Anmeldung, Pause und Ausscheiden. Spieler verwalten nur sich selbst, Admins jeden.")
class ParticipantController {

    private static final String ME = "me";
    private static final String PLAYER_ID_HELP = "Spieler-ID oder `me` für den Aufrufer";

    private final ParticipationUseCase participation;

    ParticipantController(ParticipationUseCase participation) {
        this.participation = participation;
    }

    @Operation(summary = "Zum Turnier anmelden",
            description = "Ohne Body meldet sich der Aufrufer selbst an. Ein Admin kann mit `playerId` einen anderen "
                    + "bekannten Spieler anmelden. Möglich bis zum Turnierende, wer nach dem Start kommt, spielt ab "
                    + "der nächsten Runde mit. Doppelte Anmeldung ergibt 409.")
    @PostMapping
    ResponseEntity<Responses.Tournament> register(Actor actor, @PathVariable UUID tournamentId,
            @RequestBody(required = false) Requests.Register request) {
        PlayerId player = request != null && request.playerId() != null
                ? new PlayerId(request.playerId())
                : actor.playerId();
        TournamentView view = participation.register(actor, new TournamentId(tournamentId), player);
        return ResponseEntity.status(HttpStatus.CREATED).body(RestMapper.tournament(view));
    }

    @Operation(summary = "Vom Turnier abmelden", description = "Nur vor dem Start. Danach gibt es nur Pause oder Ausscheiden.")
    @DeleteMapping("/{playerId}")
    Responses.Tournament unregister(Actor actor, @PathVariable UUID tournamentId,
            @Parameter(description = PLAYER_ID_HELP) @PathVariable String playerId) {
        return RestMapper.tournament(
                participation.unregister(actor, new TournamentId(tournamentId), resolve(playerId, actor)));
    }

    @Operation(summary = "Teilnahmestatus ändern",
            description = "`PAUSED`: ab der nächsten Runde nicht mehr zugelost (die laufende Runde wird normal "
                    + "gespielt). `ACTIVE`: wieder einsteigen. `WITHDRAWN`: ausscheiden, die Punkte bleiben "
                    + "erhalten, eine Rückkehr ist nicht möglich.")
    @PutMapping("/{playerId}/status")
    Responses.Tournament changeStatus(Actor actor, @PathVariable UUID tournamentId,
            @Parameter(description = PLAYER_ID_HELP) @PathVariable String playerId,
            @Valid @RequestBody Requests.ChangeParticipantStatus request) {
        TournamentId tournament = new TournamentId(tournamentId);
        PlayerId player = resolve(playerId, actor);
        TournamentView view = switch (request.status()) {
            case PAUSED -> participation.pause(actor, tournament, player);
            case ACTIVE -> participation.resume(actor, tournament, player);
            case WITHDRAWN -> participation.withdraw(actor, tournament, player);
        };
        return RestMapper.tournament(view);
    }

    private static PlayerId resolve(String value, Actor actor) {
        return ME.equals(value) ? actor.playerId() : PlayerId.of(value);
    }
}
