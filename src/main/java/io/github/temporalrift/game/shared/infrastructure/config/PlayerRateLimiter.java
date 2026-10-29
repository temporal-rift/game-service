package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.event.ExecutionClosed;
import io.github.temporalrift.game.shared.domain.event.ExecutionOpened;

/**
 * Per-player fixed-window request limiter. Scope is abuse throttling (join-code enumeration,
 * submission spam), not fair queuing — a fixed one-minute window is enough and keeps this
 * dependency-free. Counters are per instance; N instances allow at most N× the configured rate,
 * which is acceptable for an abuse bound.
 *
 * <p>In a simulation deployment the counters belong to the execution context they were counted in: every context
 * seats the same players and starts its logical time at the same instant, so a window counted in one context would
 * still be current in the next and carry its count over. They are dropped when a context closes and again when the
 * next one opens, which also drops what requests between two contexts counted at the logical clock's idle time.
 */
@Component
public class PlayerRateLimiter {

    private record Window(long windowStart, AtomicInteger count) {}

    private static final long WINDOW_MILLIS = 60_000;

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;

    PlayerRateLimiter(RateLimitProperties rateLimitProperties, Clock clock) {
        this.requestsPerMinute = rateLimitProperties.requestsPerMinute();
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

    /**
     * Runs while the close is being announced, before the next context can be opened, so every window it drops was
     * counted in the closed context.
     */
    @EventListener
    void onExecutionClosed(ExecutionClosed closed) {
        windows.clear();
    }

    /**
     * Runs while the context is being registered, before it commits, so no request that sees the context open can
     * have been counted before this reset.
     */
    @EventListener
    void onExecutionOpened(ExecutionOpened opened) {
        windows.clear();
    }

    @Scheduled(fixedDelayString = "${game.rate-limit.cleanup-interval}")
    void evictStaleWindows() {
        var cutoff = clock.millis() - WINDOW_MILLIS;
        windows.entrySet().removeIf(entry -> entry.getValue().windowStart() < cutoff);
    }
}
