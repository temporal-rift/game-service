package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EraSagaRecoveryMetricsTest {

    @Test
    void recordScoresUpdatedRecovery_incrementsCounterByOnePerCall() {
        var meterRegistry = new SimpleMeterRegistry();
        var metrics = new EraSagaRecoveryMetrics(meterRegistry);

        metrics.recordScoresUpdatedRecovery();
        metrics.recordScoresUpdatedRecovery();

        assertThat(meterRegistry
                        .counter(EraSagaRecoveryMetrics.SCORES_UPDATED_RECOVERY_METRIC_NAME)
                        .count())
                .isEqualTo(2.0);
    }
}
