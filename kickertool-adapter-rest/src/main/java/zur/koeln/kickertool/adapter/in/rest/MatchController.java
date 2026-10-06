package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

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
import zur.koeln.kickertool.application.port.in.MatchResultUseCase;
import zur.koeln.kickertool.application.port.in.TournamentQueries;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.NotFoundException;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.TournamentId;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/matches")
@Tag(name = "Matches und Ergebnisse",
        description = "Ein Team trägt das Ergebnis ein, das Gegnerteam bestätigt. Lehnt es ab, entscheidet der Admin.")
class MatchController {

    private final TournamentQueries queries;
    private final MatchResultUseCase results;

    MatchController(TournamentQueries queries, MatchResultUseCase results) {
        this.queries = queries;
        this.results = results;
    }

    @Operation(summary = "Meine Matches",
            description = "Alle Matches des Aufrufers in diesem Turnier, auch als Einspringer, in Reihenfolge der "
                    + "Runden. Zeigt Tisch und Status, also ob und wo man gerade spielen soll.")
    @GetMapping("/mine")
    List<Responses.Match> mine(Actor actor, @PathVariable UUID tournamentId) {
        TournamentView view = queries.getTournament(new TournamentId(tournamentId));
        return view.tournament().rounds().stream()
                .flatMap(round -> round.matches().stream())
                .filter(match -> match.sideOf(actor.playerId()).isPresent())
                .map(match -> RestMapper.match(view, match, actor))
                .toList();
    }

    @Operation(summary = "Ein Match abrufen")
    @GetMapping("/{matchId}")
    Responses.Match get(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(queries.getTournament(new TournamentId(tournamentId)), matchId, actor);
    }

    @Operation(summary = "Ergebnis eintragen",
            description = "Ein Spieler des Matches (auch ein Einspringer) trägt den Endstand ein. Das Match muss am "
                    + "Tisch spielen. Das Ergebnis zählt erst, wenn das Gegnerteam es bestätigt. Kein Team darf "
                    + "mehr Tore als das Limit haben, und beide zusammen dürfen das Limit nicht erreichen (400).")
    @PostMapping("/{matchId}/result-proposal")
    Responses.Match propose(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId,
            @Valid @RequestBody Requests.Result request) {
        return matchOf(results.submitResult(actor, new TournamentId(tournamentId), new MatchId(matchId),
                request.toDomain()), matchId, actor);
    }

    @Operation(summary = "Ergebnis bestätigen",
            description = "Ein Spieler des **gegnerischen** Teams bestätigt das eingetragene Ergebnis. Das "
                    + "eintragende Team kann nicht selbst bestätigen (403).")
    @PostMapping("/{matchId}/result-proposal/confirmation")
    Responses.Match confirm(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(results.confirmResult(actor, new TournamentId(tournamentId), new MatchId(matchId)), matchId, actor);
    }

    @Operation(summary = "Ergebnis ablehnen",
            description = "Ein Spieler des gegnerischen Teams lehnt das Ergebnis ab. Das Match hat dann den Status "
                    + "`DISPUTED`, der Admin legt das Ergebnis fest.")
    @PostMapping("/{matchId}/result-proposal/rejection")
    Responses.Match reject(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(results.rejectResult(actor, new TournamentId(tournamentId), new MatchId(matchId)), matchId, actor);
    }

    @Operation(summary = "Ergebnis als Admin festlegen oder korrigieren",
            description = "**Nur Admin.** Legt das Ergebnis eines Matches fest, das gespielt wird oder wurde, und "
                    + "bestätigt es sofort. Auch bestätigte Ergebnisse lassen sich nachträglich ändern, die "
                    + "Rangliste rechnet sich neu.")
    @PutMapping("/{matchId}/result")
    Responses.Match decide(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId,
            @Valid @RequestBody Requests.Result request) {
        return matchOf(results.decideResult(actor, new TournamentId(tournamentId), new MatchId(matchId),
                request.toDomain()), matchId, actor);
    }

    private static Responses.Match matchOf(TournamentView view, UUID matchId, Actor actor) {
        return view.tournament().findMatch(new MatchId(matchId))
                .map(match -> RestMapper.match(view, match, actor))
                .orElseThrow(() -> new NotFoundException("Match nicht gefunden: " + matchId));
    }
}
