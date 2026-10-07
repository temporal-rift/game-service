package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.port.out.OutboxProbe;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class OutboxProbeAdapter implements OutboxProbe {

    private final JdbcClient jdbc;

    OutboxProbeAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int pendingPublications() {
        return jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL")
                .query(Integer.class)
                .single();
    }
}
