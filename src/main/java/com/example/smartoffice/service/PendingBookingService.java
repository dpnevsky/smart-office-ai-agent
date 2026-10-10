package com.example.smartoffice.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingBookingService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, PendingBooking> pendingBySession = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingBookingService(Clock clock) {
        this.clock = clock;
    }

    public PendingBooking save(
            String sessionId,
            Long roomId,
            String employee,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        PendingBooking pending = new PendingBooking(
                roomId,
                employee,
                startAt,
                endAt,
                clock.instant()
        );
        pendingBySession.put(sessionId, pending);
        return pending;
    }

    public Optional<PendingBooking> get(String sessionId) {
        PendingBooking pending = pendingBySession.get(sessionId);
        if (pending == null) {
            return Optional.empty();
        }
        if (pending.createdAt().plus(TTL).isBefore(clock.instant())) {
            pendingBySession.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public boolean hasPending(String sessionId) {
        return get(sessionId).isPresent();
    }

    public Optional<PendingBooking> consume(String sessionId) {
        Optional<PendingBooking> pending = get(sessionId);
        pending.ifPresent(ignored -> pendingBySession.remove(sessionId));
        return pending;
    }

    public boolean cancel(String sessionId) {
        Optional<PendingBooking> pending = get(sessionId);
        return pending.filter(value -> pendingBySession.remove(sessionId, value)).isPresent();
    }

    public record PendingBooking(
            Long roomId,
            String employee,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Instant createdAt
    ) {
    }
}
