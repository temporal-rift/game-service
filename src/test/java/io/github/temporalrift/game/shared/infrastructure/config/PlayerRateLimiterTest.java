package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerRateLimiterTest {

    static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
    static final int LIMIT = 3;

    private final MutableClock clock = new MutableClock(NOW);
    private final PlayerRateLimiter limiter =
            new PlayerRateLimiter(new RateLimitProperties(LIMIT, Duration.ofMinutes(1)), clock);

    @Test
    @DisplayName("requests within the limit are allowed, the one over it is rejected")
    void tryAcquire_overLimit_rejected() {
        var playerId = UUID.randomUUID();

        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(playerId)).isTrue();
        }
        assertThat(limiter.tryAcquire(playerId)).isFalse();
    }

    @Test
    @DisplayName("limits are per player")
    void tryAcquire_perPlayerIsolation() {
        var throttled = UUID.randomUUID();
        var other = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            limiter.tryAcquire(throttled);
        }

        assertThat(limiter.tryAcquire(throttled)).isFalse();
        assertThat(limiter.tryAcquire(other)).isTrue();
    }

    @Test
    @DisplayName("a new window resets the budget")
    void tryAcquire_windowRollover_resets() {
        var playerId = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            limiter.tryAcquire(playerId);
        }
        assertThat(limiter.tryAcquire(playerId)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire(playerId)).isTrue();
    }

    @Test
    @DisplayName("eviction removes stale windows without affecting fresh ones")
    void evictStaleWindows_keepsFreshWindow() {
        var stale = UUID.randomUUID();
        var fresh = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            limiter.tryAcquire(stale);
        }
        clock.advance(Duration.ofSeconds(61));
        for (int i = 0; i <= LIMIT; i++) {
            limiter.tryAcquire(fresh);
        }

        limiter.evictStaleWindows();

        assertThat(limiter.tryAcquire(stale)).isTrue();
        assertThat(limiter.tryAcquire(fresh)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
