package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO processed_events (event_id, consumer, processed_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id, consumer) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    ProcessedEventRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryMarkProcessed(UUID eventId, String consumer) {
        return jdbcTemplate.update(INSERT_IF_ABSENT, Objects.requireNonNull(eventId), Objects.requireNonNull(consumer))
                == 1;
    }
}
