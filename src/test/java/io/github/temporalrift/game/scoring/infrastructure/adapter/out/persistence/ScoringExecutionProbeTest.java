package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class ScoringExecutionProbeTest {

    @Test
    @DisplayName("the probe reports resolved but unscored eras as pending continuations and owns no game or timer")
    void observe_reportsPendingScoringCompletions() {
        var jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        given(jdbc.sql(anyString()).query(Integer.class).single()).willReturn(2);

        var observation = new ScoringExecutionProbe(jdbc).observe(Instant.EPOCH);

        assertThat(observation.continuations()).isEqualTo(2);
        assertThat(observation.dueTimers()).isZero();
        assertThat(observation.gameId()).isNull();
        assertThat(observation.nextDeadline()).isNull();
    }
}
