package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimerAndClockConfigTest {

    @Test
    @DisplayName("the ordinary clock is the UTC system clock")
    void clock_isSystemUtc() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
