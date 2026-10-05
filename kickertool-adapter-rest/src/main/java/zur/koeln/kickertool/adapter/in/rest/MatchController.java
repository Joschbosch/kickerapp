package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;
import java.util.UUID;

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
class MatchController {

    private final TournamentQueries queries;
    private final MatchResultUseCase results;

    MatchController(TournamentQueries queries, MatchResultUseCase results) {
        this.queries = queries;
        this.results = results;
    }

    /** Die Matches des Aufrufers in diesem Turnier (auch als Einspringer), in Reihenfolge der Runden. */
    @GetMapping("/mine")
    List<Responses.Match> mine(Actor actor, @PathVariable UUID tournamentId) {
        TournamentView view = queries.getTournament(new TournamentId(tournamentId));
        return view.tournament().rounds().stream()
                .flatMap(round -> round.matches().stream())
                .filter(match -> match.sideOf(actor.playerId()).isPresent())
                .map(match -> RestMapper.match(view, match))
                .toList();
    }

    @GetMapping("/{matchId}")
    Responses.Match get(@PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(queries.getTournament(new TournamentId(tournamentId)), matchId);
    }

    /** Ein Spieler des Matches trägt das Ergebnis ein. Das Gegnerteam muss es bestätigen. */
    @PostMapping("/{matchId}/result-proposal")
    Responses.Match propose(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId,
            @Valid @RequestBody Requests.Result request) {
        return matchOf(results.submitResult(actor, new TournamentId(tournamentId), new MatchId(matchId),
                request.toDomain()), matchId);
    }

    /** Ein Spieler des Gegnerteams bestätigt das Ergebnis. */
    @PostMapping("/{matchId}/result-proposal/confirmation")
    Responses.Match confirm(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(results.confirmResult(actor, new TournamentId(tournamentId), new MatchId(matchId)), matchId);
    }

    /** Ein Spieler des Gegnerteams lehnt das Ergebnis ab. Der Admin entscheidet dann. */
    @PostMapping("/{matchId}/result-proposal/rejection")
    Responses.Match reject(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId) {
        return matchOf(results.rejectResult(actor, new TournamentId(tournamentId), new MatchId(matchId)), matchId);
    }

    /** Nur für Admins: legt das Ergebnis fest oder korrigiert es, auch nachträglich. */
    @PutMapping("/{matchId}/result")
    Responses.Match decide(Actor actor, @PathVariable UUID tournamentId, @PathVariable UUID matchId,
            @Valid @RequestBody Requests.Result request) {
        return matchOf(results.decideResult(actor, new TournamentId(tournamentId), new MatchId(matchId),
                request.toDomain()), matchId);
    }

    private static Responses.Match matchOf(TournamentView view, UUID matchId) {
        return view.tournament().findMatch(new MatchId(matchId))
                .map(match -> RestMapper.match(view, match))
                .orElseThrow(() -> new NotFoundException("Match nicht gefunden: " + matchId));
    }
}
