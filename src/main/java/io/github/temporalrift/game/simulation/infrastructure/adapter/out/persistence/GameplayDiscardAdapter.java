package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.port.out.GameplayDiscard;

@Component
@Profile("!in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class GameplayDiscardAdapter implements GameplayDiscard {

    private static final String DISCARD = """
            DO $$
            DECLARE gameplay record;
            BEGIN
                FOR gameplay IN
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = current_schema()
                      AND tablename NOT LIKE 'simulation!_%' ESCAPE '!'
                      AND tablename NOT LIKE 'databasechangelog%'
                LOOP
                    EXECUTE format('TRUNCATE TABLE %I RESTART IDENTITY CASCADE', gameplay.tablename);
                END LOOP;
            END $$
            """;

    private final JdbcClient jdbc;

    GameplayDiscardAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every table of the schema except the control state and the migration history holds gameplay state. */
    @Override
    public void discardAll() {
        jdbc.sql(DISCARD).update();
    }
}
