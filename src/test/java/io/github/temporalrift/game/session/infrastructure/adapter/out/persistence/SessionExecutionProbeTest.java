package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

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
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class SessionExecutionProbeTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final UUID GAME = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

    @Test
    @DisplayName("the probe reports the hosted game, its end, due grace periods, pending inbox facts and next deadline")
    @SuppressWarnings("unchecked")
    void observe_reportsGameStateAndPendingWork() {
        given(jdbc.sql(anyString()).query(UUID.class).optional()).willReturn(Optional.of(GAME));
        given(jdbc.sql(anyString()).query(Boolean.class).single()).willReturn(true);
        given(jdbc.sql(anyString()).query(Integer.class).single()).willReturn(4);
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(Integer.class)
                        .single())
                .willReturn(1);
        given(jdbc.sql(anyString())
                        .param(anyString(), any())
                        .query(any(RowMapper.class))
                        .optional())
                .willReturn(Optional.of(OffsetDateTime.ofInstant(NOW.plusSeconds(90), ZoneOffset.UTC)));

        var observation = new SessionExecutionProbe(jdbc).observe(NOW);

        assertThat(observation.gameId()).isEqualTo(GAME);
        assertThat(observation.gameEnded()).isTrue();
        assertThat(observation.dueTimers()).isEqualTo(1);
        assertThat(observation.continuations()).isEqualTo(4);
        assertThat(observation.nextDeadline()).isEqualTo(NOW.plusSeconds(90));
    }

    @Test
    @DisplayName("before a lobby exists the probe reports no game")
    @SuppressWarnings("unchecked")
    void observe_withoutLobby_hasNoGame() {
        given(jdbc.sql(anyString()).query(UUID.class).optional()).willReturn(Optional.empty());
        given(jdbc.sql(anyString()).query(Boolean.class).single()).willReturn(false);
        given(jdbc.sql(anyString()).query(Integer.class).single()).willReturn(0);
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

        var observation = new SessionExecutionProbe(jdbc).observe(NOW);

        assertThat(observation.gameId()).isNull();
        assertThat(observation.gameEnded()).isFalse();
        assertThat(observation.nextDeadline()).isNull();
    }
}
