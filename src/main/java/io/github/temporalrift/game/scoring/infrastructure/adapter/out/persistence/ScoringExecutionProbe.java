package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
class ScoringExecutionProbe implements ExecutionProbe {

    private final JdbcClient jdbc;

    ScoringExecutionProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Observation observe(Instant now) {
        int continuations = jdbc.sql("""
                        SELECT count(*) FROM scoring_timeline_resolution_barrier barrier
                        LEFT JOIN scoring_era_completion completion
                          ON completion.game_id = barrier.game_id AND completion.era_number = barrier.era_number
                        WHERE completion.game_id IS NULL
                        """).query(Integer.class).single();
        return new Observation(null, false, 0, continuations, null);
    }
}
