package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
class ActionExecutionProbe implements ExecutionProbe {

    private static final String OPEN_DEADLINES = """
            SELECT selection_expires_at AS deadline FROM hand_selection WHERE status = 'OPEN'
            UNION ALL
            SELECT expires_at FROM declaration_phase WHERE status = 'OPEN'
            UNION ALL
            SELECT timer_expires_at FROM action_round_saga_state WHERE status = 'WAITING'
            """;

    private final JdbcClient jdbc;

    ActionExecutionProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Observation observe(Instant now) {
        var at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        int due = jdbc.sql("SELECT count(*) FROM (" + OPEN_DEADLINES + ") open_deadline WHERE deadline <= :now")
                .param("now", at)
                .query(Integer.class)
                .single();
        var next = jdbc.sql("SELECT min(deadline) FROM (" + OPEN_DEADLINES + ") open_deadline WHERE deadline > :now")
                .param("now", at)
                .query((rs, row) -> rs.getObject(1, OffsetDateTime.class))
                .optional()
                .map(OffsetDateTime::toInstant)
                .orElse(null);
        return new Observation(null, false, due, 0, next);
    }
}
