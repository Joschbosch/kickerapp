package zur.koeln.kickertool.adapter.in.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    private static final String TOURNAMENTS = "Turniere";
    private static final String ROUNDS = "Rangliste und Runden";

    private final TournamentManagementUseCase management;
    private final TournamentQueries queries;

    TournamentController(TournamentManagementUseCase management, TournamentQueries queries) {
        this.management = management;
        this.queries = queries;
    }

    @Operation(tags = TOURNAMENTS, summary = "Turniere auflisten", description = "Neueste zuerst.")
    @GetMapping
    List<Responses.TournamentSummary> list() {
        return queries.listTournaments().stream().map(RestMapper::summary).toList();
    }

    @Operation(tags = TOURNAMENTS, summary = "Turnier planen",
            description = "**Nur Admin.** Ohne `config` gelten die Standardwerte (1 Tisch, 3/1/0 Punkte, 10 Tore, "
                    + "5 Minuten, 2 Zufallsrunden). Danach können sich Spieler anmelden.")
    @PostMapping
    ResponseEntity<Responses.Tournament> plan(Actor actor, @Valid @RequestBody Requests.PlanTournament request) {
        TournamentView view = management.plan(actor, request.name(), request.date(),
                request.config() != null ? request.config().toDomain() : null);
        UUID id = view.tournament().id().value();
        return ResponseEntity.created(URI.create("/api/tournaments/" + id)).body(RestMapper.tournament(view, actor));
    }

    @Operation(tags = TOURNAMENTS, summary = "Turnier abrufen",
            description = "Mit Konfiguration, Teilnehmern und der aktuellen Runde samt Matches.")
    @GetMapping("/{id}")
    Responses.Tournament get(Actor actor, @Parameter(description = "Turnier-ID") @PathVariable UUID id) {
        return RestMapper.tournament(queries.getTournament(new TournamentId(id)), actor);
    }

    @Operation(tags = TOURNAMENTS, summary = "Name und Datum ändern", description = "**Nur Admin.**")
    @PutMapping("/{id}")
    Responses.Tournament update(Actor actor, @PathVariable UUID id, @Valid @RequestBody Requests.UpdateTournament request) {
        return RestMapper.tournament(
                management.updateDetails(actor, new TournamentId(id), request.name(), request.date()), actor);
    }

    @Operation(tags = TOURNAMENTS, summary = "Konfiguration ersetzen",
            description = "**Nur Admin.** Auch während des Turniers möglich, z. B. wenn ein Tisch ausfällt. Eine "
                    + "neue Tischzahl gilt ab der nächsten Welle, eine geänderte Punktevergabe wirkt sofort auf "
                    + "die Rangliste.")
    @PutMapping("/{id}/config")
    Responses.Tournament updateConfig(Actor actor, @PathVariable UUID id, @Valid @RequestBody Requests.Config request) {
        return RestMapper.tournament(management.updateConfig(actor, new TournamentId(id), request.toDomain()), actor);
    }

    @Operation(tags = TOURNAMENTS, summary = "Turnier starten",
            description = "**Nur Admin.** Danach laufen die Runden. Anmelden ist weiterhin möglich, "
                    + "neue Spieler spielen ab der nächsten Runde mit.")
    @PostMapping("/{id}/start")
    Responses.Tournament start(Actor actor, @PathVariable UUID id) {
        return RestMapper.tournament(management.start(actor, new TournamentId(id)), actor);
    }

    @Operation(tags = TOURNAMENTS, summary = "Nächste Runde auslosen",
            description = "**Nur Admin.** Lost die aktiven Spieler zu Teams, setzt die ersten Matches an die Tische "
                    + "und stellt die übrigen in die Warteschlange. Erst möglich, wenn alle Ergebnisse der letzten "
                    + "Runde bestätigt sind (sonst 409).")
    @PostMapping("/{id}/rounds")
    ResponseEntity<Responses.Tournament> startNextRound(Actor actor, @PathVariable UUID id) {
        TournamentView view = management.startNextRound(actor, new TournamentId(id));
        return ResponseEntity.created(URI.create("/api/tournaments/" + id + "/rounds/current"))
                .body(RestMapper.tournament(view, actor));
    }

    @Operation(tags = TOURNAMENTS, summary = "Turnier beenden",
            description = "**Nur Admin.** Nur möglich, wenn alle Ergebnisse bestätigt sind.")
    @PostMapping("/{id}/finish")
    Responses.Tournament finish(Actor actor, @PathVariable UUID id) {
        return RestMapper.tournament(management.finish(actor, new TournamentId(id)), actor);
    }

    @Operation(tags = ROUNDS, summary = "Rangliste",
            description = "Einzelrangliste nach Punkten, Tordifferenz und erzielten Toren. Es zählen nur bestätigte "
                    + "Ergebnisse. Ausgeschiedene Spieler sind mit Status `WITHDRAWN` enthalten.")
    @GetMapping("/{id}/ranking")
    List<Responses.RankingEntry> ranking(@PathVariable UUID id) {
        return RestMapper.ranking(queries.getTournament(new TournamentId(id)));
    }

    @Operation(tags = ROUNDS, summary = "Alle Runden mit Matches")
    @GetMapping("/{id}/rounds")
    List<Responses.Round> rounds(Actor actor, @PathVariable UUID id) {
        return RestMapper.rounds(queries.getTournament(new TournamentId(id)), actor);
    }

    @Operation(tags = ROUNDS, summary = "Aktuelle Runde", description = "404, solange noch keine Runde gestartet wurde.")
    @GetMapping("/{id}/rounds/current")
    Responses.Round currentRound(Actor actor, @PathVariable UUID id) {
        TournamentView view = queries.getTournament(new TournamentId(id));
        return view.tournament().currentRound()
                .map(round -> RestMapper.round(view, round, actor))
                .orElseThrow(() -> new NotFoundException("Es läuft noch keine Runde"));
    }
}
