package zur.koeln.kickertool.adapter.in.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zur.koeln.kickertool.adapter.in.rest.dto.Requests;
import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.port.in.TournamentManagementUseCase;
import zur.koeln.kickertool.application.port.in.TournamentQueries;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@RestController
@RequestMapping("/api/tournaments")
class TournamentController {

    private final TournamentManagementUseCase management;
    private final TournamentQueries queries;

    TournamentController(TournamentManagementUseCase management, TournamentQueries queries) {
        this.management = management;
        this.queries = queries;
    }

    @GetMapping
    List<Responses.TournamentSummary> list() {
        return queries.listTournaments().stream().map(RestMapper::summary).toList();
    }

    /** Plant ein Turnier. Nur Admins. */
    @PostMapping
    ResponseEntity<Responses.Tournament> plan(Actor actor, @Valid @RequestBody Requests.PlanTournament request) {
        TournamentView view = management.plan(actor, request.name(), request.date(),
                request.config() != null ? request.config().toDomain() : null);
        UUID id = view.tournament().id().value();
        return ResponseEntity.created(URI.create("/api/tournaments/" + id)).body(RestMapper.tournament(view));
    }

    @GetMapping("/{id}")
    Responses.Tournament get(@PathVariable UUID id) {
        return RestMapper.tournament(queries.getTournament(new TournamentId(id)));
    }

    @PutMapping("/{id}")
    Responses.Tournament update(Actor actor, @PathVariable UUID id, @Valid @RequestBody Requests.UpdateTournament request) {
        return RestMapper.tournament(
                management.updateDetails(actor, new TournamentId(id), request.name(), request.date()));
    }

    /** Ersetzt die Konfiguration. Auch während des Turniers möglich, eine neue Tischzahl gilt ab der nächsten Welle. */
    @PutMapping("/{id}/config")
    Responses.Tournament updateConfig(Actor actor, @PathVariable UUID id, @Valid @RequestBody Requests.Config request) {
        return RestMapper.tournament(management.updateConfig(actor, new TournamentId(id), request.toDomain()));
    }

    @PostMapping("/{id}/start")
    Responses.Tournament start(Actor actor, @PathVariable UUID id) {
        return RestMapper.tournament(management.start(actor, new TournamentId(id)));
    }

    /** Startet die nächste Runde. Nur möglich, wenn alle Ergebnisse der vorigen Runde bestätigt sind. */
    @PostMapping("/{id}/rounds")
    ResponseEntity<Responses.Tournament> startNextRound(Actor actor, @PathVariable UUID id) {
        TournamentView view = management.startNextRound(actor, new TournamentId(id));
        return ResponseEntity.created(URI.create("/api/tournaments/" + id + "/rounds/current"))
                .body(RestMapper.tournament(view));
    }

    @PostMapping("/{id}/finish")
    Responses.Tournament finish(Actor actor, @PathVariable UUID id) {
        return RestMapper.tournament(management.finish(actor, new TournamentId(id)));
    }

    @GetMapping("/{id}/ranking")
    List<Responses.RankingEntry> ranking(@PathVariable UUID id) {
        return RestMapper.ranking(queries.getTournament(new TournamentId(id)));
    }

    @GetMapping("/{id}/rounds")
    List<Responses.Round> rounds(@PathVariable UUID id) {
        return RestMapper.rounds(queries.getTournament(new TournamentId(id)));
    }

    @GetMapping("/{id}/rounds/current")
    Responses.Round currentRound(@PathVariable UUID id) {
        TournamentView view = queries.getTournament(new TournamentId(id));
        return view.tournament().currentRound()
                .map(round -> RestMapper.round(view, round))
                .orElseThrow(() -> new NotFoundException("Es läuft noch keine Runde"));
    }
}
