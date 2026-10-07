package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class ActionExecutionProbeTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

    @Test
    @DisplayName("the probe reports due timers and the earliest later deadline, and no game or continuations")
    @SuppressWarnings("unchecked")
    void observe_reportsDueTimersAndNextDeadline() {
        var next = OffsetDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC);
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(Integer.class)
                        .single())
                .willReturn(2);
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(any(RowMapper.class))
                        .optional())
                .willReturn(Optional.of(next));

        var observation = new ActionExecutionProbe(jdbc).observe(NOW);

        assertThat(observation.dueTimers()).isEqualTo(2);
        assertThat(observation.nextDeadline()).isEqualTo(NOW.plusSeconds(30));
        assertThat(observation.gameId()).isNull();
        assertThat(observation.gameEnded()).isFalse();
        assertThat(observation.continuations()).isZero();
    }

    @Test
    @DisplayName("without any open timer the probe reports no next deadline")
    @SuppressWarnings("unchecked")
    void observe_withoutOpenTimers_hasNoDeadline() {
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(Integer.class)
                        .single())
                .willReturn(0);
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(any(RowMapper.class))
                        .optional())
                .willReturn(Optional.empty());

        assertThat(new ActionExecutionProbe(jdbc).observe(NOW).nextDeadline()).isNull();
    }
}
