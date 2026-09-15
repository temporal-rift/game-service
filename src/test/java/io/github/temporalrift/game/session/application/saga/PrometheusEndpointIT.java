package io.github.temporalrift.game.session.application.saga;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import io.github.temporalrift.game.GameServiceIntegrationTest;

/**
 * Proves the Prometheus scrape endpoint is available without authentication and carries this
 * service's custom counters. Lives in the saga package to reuse the package-private
 * sweep-recovery metric name instead of duplicating its literal.
 */
@GameServiceIntegrationTest
class PrometheusEndpointIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Unauthenticated scrape of /actuator/prometheus responds 200 with the sweep-recovery counter")
    void scrapeEndpoint_exposesSweepRecoveryCounterWithoutAuthentication() throws Exception {
        meterRegistry
                .counter(EraSagaRecoveryMetrics.SCORES_UPDATED_RECOVERY_METRIC_NAME)
                .increment();

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("game_session_era_saga_scores_updated_recovery")));
    }
}
