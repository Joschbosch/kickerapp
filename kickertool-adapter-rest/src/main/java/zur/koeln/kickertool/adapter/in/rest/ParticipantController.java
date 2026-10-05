package zur.koeln.kickertool.adapter.in.rest;

import java.util.UUID;

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
class ParticipantController {

    private static final String ME = "me";

    private final ParticipationUseCase participation;

    ParticipantController(ParticipationUseCase participation) {
        this.participation = participation;
    }

    /** Meldet an. Ohne Body oder {@code playerId} meldet sich der Aufrufer selbst an. */
    @PostMapping
    ResponseEntity<Responses.Tournament> register(Actor actor, @PathVariable UUID tournamentId,
            @RequestBody(required = false) Requests.Register request) {
        PlayerId player = request != null && request.playerId() != null
                ? new PlayerId(request.playerId())
                : actor.playerId();
        TournamentView view = participation.register(actor, new TournamentId(tournamentId), player);
        return ResponseEntity.status(HttpStatus.CREATED).body(RestMapper.tournament(view));
    }

    /** Meldet ab, nur vor dem Start des Turniers. */
    @DeleteMapping("/{playerId}")
    Responses.Tournament unregister(Actor actor, @PathVariable UUID tournamentId, @PathVariable String playerId) {
        return RestMapper.tournament(
                participation.unregister(actor, new TournamentId(tournamentId), resolve(playerId, actor)));
    }

    /**
     * Setzt den Teilnahmestatus: {@code PAUSED} (ab nächster Runde pausieren), {@code ACTIVE} (wieder einsteigen)
     * oder {@code WITHDRAWN} (ausscheiden, Punkte bleiben erhalten).
     */
    @PutMapping("/{playerId}/status")
    Responses.Tournament changeStatus(Actor actor, @PathVariable UUID tournamentId, @PathVariable String playerId,
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
