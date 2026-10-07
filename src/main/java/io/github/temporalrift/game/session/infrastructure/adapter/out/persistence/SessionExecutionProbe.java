package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
class SessionExecutionProbe implements ExecutionProbe {

    private final JdbcClient jdbc;

    SessionExecutionProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Observation observe(Instant now) {
        var at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        var gameId = jdbc.sql("SELECT game_id FROM lobby ORDER BY id LIMIT 1")
                .query(UUID.class)
                .optional()
                .orElse(null);
        boolean ended = jdbc.sql("SELECT EXISTS (SELECT 1 FROM game WHERE status <> 'IN_PROGRESS')")
                .query(Boolean.class)
                .single();
        int due = jdbc.sql("""
                        SELECT count(*) FROM player_reconnect_saga_state
                        WHERE status = 'GRACE_PERIOD' AND grace_expires_at <= :now
                        """).param("now", at).query(Integer.class).single();
        int continuations = jdbc.sql("""
                        SELECT count(*) FROM era_saga_scores_updated_inbox inbox
                        JOIN era_saga_state saga
                          ON saga.game_id = inbox.game_id AND saga.era_number = inbox.era_number
                        WHERE saga.status = 'WAITING_SCORES'
                        """).query(Integer.class).single();
        var next = jdbc.sql("""
                        SELECT min(grace_expires_at) FROM player_reconnect_saga_state
                        WHERE status = 'GRACE_PERIOD' AND grace_expires_at > :now
                        """)
                .param("now", at)
                .query((rs, row) -> rs.getObject(1, OffsetDateTime.class))
                .optional()
                .map(OffsetDateTime::toInstant)
                .orElse(null);
        return new Observation(gameId, ended, due, continuations, next);
    }
}
