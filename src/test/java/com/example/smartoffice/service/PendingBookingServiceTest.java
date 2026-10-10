package com.example.smartoffice.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PendingBookingServiceTest {

    @Test
    void shouldConsumePendingBookingOnlyOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC);
        PendingBookingService service = new PendingBookingService(clock);

        service.save(
                "session-1",
                10L,
                "demo-user",
                LocalDateTime.of(2026, 8, 14, 14, 0),
                LocalDateTime.of(2026, 8, 14, 15, 0)
        );

        assertThat(service.hasPending("session-1")).isTrue();
        assertThat(service.consume("session-1")).isPresent();
        assertThat(service.hasPending("session-1")).isFalse();
        assertThat(service.consume("session-1")).isEmpty();
    }

    @Test
    void shouldNotReportExpiredBookingAsPending() {
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        MutableClock clock = new MutableClock(now);
        PendingBookingService service = new PendingBookingService(clock);
        service.save(
                "session-1",
                10L,
                "demo-user",
                LocalDateTime.of(2026, 8, 14, 14, 0),
                LocalDateTime.of(2026, 8, 14, 15, 0)
        );

        clock.instant = now.plusSeconds(601);

        assertThat(service.hasPending("session-1")).isFalse();
    }

    @Test
    void shouldReportWhetherPendingBookingWasActuallyCancelled() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC);
        PendingBookingService service = new PendingBookingService(clock);
        service.save(
                "session-1",
                10L,
                "demo-user",
                LocalDateTime.of(2026, 8, 14, 14, 0),
                LocalDateTime.of(2026, 8, 14, 15, 0)
        );

        assertThat(service.cancel("session-1")).isTrue();
        assertThat(service.cancel("session-1")).isFalse();
        assertThat(service.hasPending("session-1")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
