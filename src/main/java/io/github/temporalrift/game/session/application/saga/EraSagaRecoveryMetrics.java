package io.github.temporalrift.game.session.application.saga;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class EraSagaRecoveryMetrics {

    static final String SCORES_UPDATED_RECOVERY_METRIC_NAME = "game.session.era-saga.scores-updated-recovery";

    private final MeterRegistry meterRegistry;

    EraSagaRecoveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordScoresUpdatedRecovery() {
        meterRegistry.counter(SCORES_UPDATED_RECOVERY_METRIC_NAME).increment();
    }
}
