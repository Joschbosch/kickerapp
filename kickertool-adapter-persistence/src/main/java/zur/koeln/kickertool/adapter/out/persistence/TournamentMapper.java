package zur.koeln.kickertool.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import zur.koeln.kickertool.domain.player.PlayerId;
import zur.koeln.kickertool.domain.tournament.DummySlot;
import zur.koeln.kickertool.domain.tournament.Match;
import zur.koeln.kickertool.domain.tournament.MatchId;
import zur.koeln.kickertool.domain.tournament.MatchResult;
import zur.koeln.kickertool.domain.tournament.Participant;
import zur.koeln.kickertool.domain.tournament.PlayerSlot;
import zur.koeln.kickertool.domain.tournament.Round;
import zur.koeln.kickertool.domain.tournament.Slot;
import zur.koeln.kickertool.domain.tournament.Team;
import zur.koeln.kickertool.domain.tournament.Tournament;
import zur.koeln.kickertool.domain.tournament.TournamentConfig;
import zur.koeln.kickertool.domain.tournament.TournamentId;

/** Überträgt das Turnier-Aggregat zwischen Domänenmodell und JPA-Entitäten. */
final class TournamentMapper {

    private TournamentMapper() {
    }

    // ---------------------------------------------------------------- Entität → Domäne

    static Tournament toDomain(TournamentEntity entity, List<ParticipantEntity> participants,
            List<MatchEntity> matches) {
        TournamentConfig config = new TournamentConfig(entity.tableCount, entity.pointsWin, entity.pointsDraw,
                entity.pointsLoss, entity.goalLimit, entity.matchMinutes, entity.plannedRounds, entity.randomRounds);

        List<Participant> domainParticipants = participants.stream()
                .map(p -> Participant.restore(new PlayerId(p.playerId), p.registeredAt, p.status))
                .toList();

        Map<Integer, List<Match>> byRound = new TreeMap<>();
        for (MatchEntity match : matches) {
            byRound.computeIfAbsent(match.roundNumber, n -> new ArrayList<>()).add(toDomain(match));
        }
        List<Round> rounds = byRound.entrySet().stream()
                .map(e -> Round.restore(e.getKey(), e.getValue()))
                .toList();

        return Tournament.restore(new TournamentId(entity.id), entity.name, entity.date, entity.status, config,
                domainParticipants, rounds);
    }

    private static Match toDomain(MatchEntity m) {
        MatchResult result = m.goalsA != null && m.goalsB != null ? new MatchResult(m.goalsA, m.goalsB) : null;
        return Match.restore(new MatchId(m.id), m.roundNumber, m.queuePosition,
                new Team(toSlot(m.slotA1), toSlot(m.slotA2)), new Team(toSlot(m.slotB1), toSlot(m.slotB2)),
                m.status, m.tableNumber, result,
                m.resultEnteredBy != null ? new PlayerId(m.resultEnteredBy) : null,
                m.resultEnteredSide, m.resultSource);
    }

    private static Slot toSlot(SlotColumns columns) {
        PlayerId player = columns.playerId != null ? new PlayerId(columns.playerId) : null;
        return columns.dummy ? new DummySlot(player) : new PlayerSlot(player);
    }

    // ---------------------------------------------------------------- Domäne → Entität

    static void copyTo(Tournament tournament, TournamentEntity entity) {
        TournamentConfig config = tournament.config();
        entity.name = tournament.name();
        entity.date = tournament.date();
        entity.status = tournament.status();
        entity.tableCount = config.tableCount();
        entity.pointsWin = config.pointsWin();
        entity.pointsDraw = config.pointsDraw();
        entity.pointsLoss = config.pointsLoss();
        entity.goalLimit = config.goalLimit();
        entity.matchMinutes = config.matchMinutes();
        entity.plannedRounds = config.plannedRounds();
        entity.randomRounds = config.randomRounds();
    }

    static void copyTo(Participant participant, ParticipantEntity entity) {
        entity.status = participant.status();
        entity.registeredAt = participant.registeredAt();
    }

    static void copyTo(Match match, MatchEntity entity) {
        entity.roundNumber = match.roundNumber();
        entity.queuePosition = match.position();
        entity.status = match.status();
        entity.tableNumber = match.tableNumber().orElse(null);
        entity.slotA1 = toColumns(match.teamA().first());
        entity.slotA2 = toColumns(match.teamA().second());
        entity.slotB1 = toColumns(match.teamB().first());
        entity.slotB2 = toColumns(match.teamB().second());
        entity.goalsA = match.result().map(MatchResult::goalsA).orElse(null);
        entity.goalsB = match.result().map(MatchResult::goalsB).orElse(null);
        entity.resultEnteredBy = match.resultEnteredBy().map(PlayerId::value).orElse(null);
        entity.resultEnteredSide = match.resultEnteredSide().orElse(null);
        entity.resultSource = match.resultSource().orElse(null);
    }

    private static SlotColumns toColumns(Slot slot) {
        return switch (slot) {
            case PlayerSlot p -> new SlotColumns(false, p.playerId().value());
            case DummySlot d -> new SlotColumns(true, d.standInPlayer().map(PlayerId::value).orElse(null));
        };
    }
}
