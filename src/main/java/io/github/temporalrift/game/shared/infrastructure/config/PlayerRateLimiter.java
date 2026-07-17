package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-player fixed-window request limiter. Scope is abuse throttling (join-code enumeration,
 * submission spam), not fair queuing — a fixed one-minute window is enough and keeps this
 * dependency-free. Counters are per instance; N instances allow at most N× the configured rate,
 * which is acceptable for an abuse bound.
 */
@Component
public class PlayerRateLimiter {

    private record Window(long windowStart, AtomicInteger count) {}

    private static final long WINDOW_MILLIS = 60_000;

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;

    PlayerRateLimiter(@Value("${game.rate-limit.requests-per-minute:120}") int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    boolean tryAcquire(UUID playerId) {
        var now = clock.millis();
        var window = windows.compute(
                playerId,
                (_, current) -> current == null || now - current.windowStart() >= WINDOW_MILLIS
                        ? new Window(now, new AtomicInteger())
                        : current);
        return window.count().incrementAndGet() <= requestsPerMinute;
    }

    @Scheduled(fixedDelayString = "${game.rate-limit.cleanup-ms:300000}")
    void evictStaleWindows() {
        var cutoff = clock.millis() - WINDOW_MILLIS;
        windows.entrySet().removeIf(entry -> entry.getValue().windowStart() < cutoff);
    }
}
