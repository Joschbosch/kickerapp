package zur.koeln.kickertool.adapter.in.rest;

import java.util.List;

import zur.koeln.kickertool.adapter.in.rest.dto.Responses;
import zur.koeln.kickertool.application.Actor;
import zur.koeln.kickertool.application.event.TournamentEvent;
import zur.koeln.kickertool.application.view.TournamentSummary;
import zur.koeln.kickertool.application.view.TournamentView;
import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.DummySlot;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.Participant;
import zur.koeln.kickertool.domain.tournament.PlayerSlot;
import zur.koeln.kickertool.domain.tournament.RankingEntry;
import zur.koeln.kickertool.domain.tournament.Round;
import zur.koeln.kickertool.domain.tournament.Slot;
import zur.koeln.kickertool.domain.tournament.Team;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentStatus;

/**
 * Übersetzt Domänen- und Anwendungsobjekte in die Antwortformate der API. Die Matches enthalten, was der
 * anfragende Spieler damit tun darf ({@code viewer}), damit Clients diese Regeln nicht nachbauen müssen.
 */
final class RestMapper {

    private static final String UNKNOWN_PLAYER = "Unbekannt";

    private RestMapper() {
    }

    static Responses.Player player(zur.koeln.kickertool.domain.player.Player player) {
        return new Responses.Player(player.id().value(), player.displayName());
    }

    static Responses.Player player(TournamentView view, PlayerId id) {
        return view.player(id)
                .map(RestMapper::player)
                .orElseGet(() -> new Responses.Player(id.value(), UNKNOWN_PLAYER));
    }

    static Responses.TournamentSummary summary(TournamentSummary s) {
        return new Responses.TournamentSummary(s.id().value(), s.name(), s.date(), s.status(), s.participantCount(),
                s.roundCount());
    }

    static Responses.Config config(TournamentConfig c) {
        return new Responses.Config(c.tableCount(), c.pointsWin(), c.pointsDraw(), c.pointsLoss(), c.goalLimit(),
                c.matchMinutes(), c.plannedRounds(), c.randomRounds());
    }

    static Responses.Tournament tournament(TournamentView view, Actor viewer) {
        Tournament t = view.tournament();
        List<Responses.Participant> participants = t.participants().stream()
                .map(p -> participant(view, p))
                .toList();
        Responses.Round current = t.currentRound().map(r -> round(view, r, viewer)).orElse(null);
        return new Responses.Tournament(t.id().value(), t.name(), t.date(), t.status(), config(t.config()),
                participants, t.rounds().size(), current);
    }

    private static Responses.Participant participant(TournamentView view, Participant p) {
        return new Responses.Participant(player(view, p.playerId()), p.status(), p.registeredAt());
    }

    static Responses.Round round(TournamentView view, Round round, Actor viewer) {
        return new Responses.Round(round.number(), round.isComplete(),
                round.matches().stream().map(m -> match(view, m, viewer)).toList());
    }

    static List<Responses.Round> rounds(TournamentView view, Actor viewer) {
        return view.tournament().rounds().stream().map(r -> round(view, r, viewer)).toList();
    }

    static Responses.Match match(TournamentView view, Match m, Actor viewer) {
        Responses.Result result = m.result()
                .map(r -> new Responses.Result(r.goalsA(), r.goalsB(), m.isConfirmed(),
                        m.resultEnteredBy().map(id -> player(view, id)).orElse(null),
                        m.resultEnteredSide().orElse(null), m.resultSource().orElse(null)))
                .orElse(null);
        return new Responses.Match(m.id().value(), m.roundNumber(), m.position(), m.status(),
                m.tableNumber().orElse(null), team(view, m.teamA()), team(view, m.teamB()), result,
                m.sideOf(viewer.playerId()).orElse(null), permissions(view.tournament().status(), m, viewer));
    }

    /** Ergebnisse eintragen und bestätigen geht nur im laufenden Turnier, der Admin darf auch danach korrigieren. */
    private static Responses.MatchPermissions permissions(TournamentStatus status, Match match, Actor viewer) {
        boolean running = status == TournamentStatus.RUNNING;
        boolean respond = running && match.canRespondToResult(viewer.playerId());
        return new Responses.MatchPermissions(
                running && match.canEnterResult(viewer.playerId()),
                respond,
                respond,
                viewer.admin() && match.canBeDecidedByAdmin());
    }

    private static Responses.Team team(TournamentView view, Team team) {
        return new Responses.Team(team.slots().stream().map(s -> slot(view, s)).toList());
    }

    private static Responses.Slot slot(TournamentView view, Slot slot) {
        return switch (slot) {
            case PlayerSlot p -> new Responses.Slot("PLAYER", player(view, p.playerId()), null);
            case DummySlot d -> new Responses.Slot("DUMMY", null,
                    d.standInPlayer().map(id -> player(view, id)).orElse(null));
        };
    }

    static List<Responses.RankingEntry> ranking(TournamentView view) {
        return view.tournament().ranking().entries().stream().map(e -> rankingEntry(view, e)).toList();
    }

    private static Responses.RankingEntry rankingEntry(TournamentView view, RankingEntry e) {
        return new Responses.RankingEntry(e.rank(), player(view, e.playerId()), e.status(), e.matchesPlayed(),
                e.wins(), e.draws(), e.losses(), e.goalsFor(), e.goalsAgainst(), e.goalDifference(), e.points());
    }

    static Responses.Event event(TournamentEvent event) {
        return new Responses.Event(event.type().name(), event.matchId() != null ? event.matchId().value() : null,
                event.occurredAt());
    }
}
