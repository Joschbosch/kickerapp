package zur.koeln.kickertool.adapter.in.rest.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class EventTicketServiceTest {

    private static final Duration TTL = Duration.ofMinutes(2);

    /** Eine Uhr, die der Test vorstellt. */
    private static final class SettableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final SettableClock clock = new SettableClock();
    private final EventTicketService service = new EventTicketService(clock, TTL);
    private final UUID tournament = UUID.randomUUID();
    private final Authentication anna = new TestingAuthenticationToken("anna", "n/a", "ROLE_X");

    @Test
    void redeemsATicketForTheTournamentItWasIssuedFor() {
        String ticket = service.issue(tournament, anna);

        assertThat(service.redeem(ticket, tournament)).containsSame(anna);
    }

    @Test
    void rejectsTheTicketForAnotherTournament() {
        String ticket = service.issue(tournament, anna);

        assertThat(service.redeem(ticket, UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsUnknownOrMissingTickets() {
        assertThat(service.redeem("unbekannt", tournament)).isEmpty();
        assertThat(service.redeem("", tournament)).isEmpty();
        assertThat(service.redeem(null, tournament)).isEmpty();
    }

    @Test
    void ticketsStayValidForTheConfiguredTimeAndCanBeUsedRepeatedly() {
        String ticket = service.issue(tournament, anna);

        clock.advance(Duration.ofSeconds(119));
        assertThat(service.redeem(ticket, tournament)).isPresent();
        assertThat(service.redeem(ticket, tournament)).as("mehrfach").isPresent();
    }

    @Test
    void ticketsExpire() {
        String ticket = service.issue(tournament, anna);

        clock.advance(TTL);

        assertThat(service.redeem(ticket, tournament)).isEmpty();
        assertThat(service.size()).as("abgelaufene Tickets werden entfernt").isZero();
    }

    @Test
    void issuingCleansUpExpiredTickets() {
        service.issue(tournament, anna);
        service.issue(tournament, anna);
        clock.advance(TTL.plusSeconds(1));

        service.issue(tournament, anna);

        assertThat(service.size()).isEqualTo(1);
    }

    @Test
    void ticketsAreLongAndUnique() {
        Set<String> tickets = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tickets.add(service.issue(tournament, anna));
        }

        assertThat(tickets).hasSize(200);
        assertThat(tickets).allSatisfy(ticket -> assertThat(ticket).hasSize(43).matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void reportsTheConfiguredLifetime() {
        assertThat(service.ttl()).isEqualTo(TTL);
    }
}
