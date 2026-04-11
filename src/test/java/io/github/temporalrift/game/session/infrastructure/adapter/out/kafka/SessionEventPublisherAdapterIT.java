package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SessionEventPublisherAdapterIT {

    @Autowired
    SessionEventPublisher sessionEventPublisher;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM event_publication");
    }

    @Test
    void publish_storesEventInOutbox_withinSameTransactionAsAggregateSave() {
        final var lobbyId = UUID.randomUUID();
        final var gameId = UUID.randomUUID();
        final var hostId = UUID.randomUUID();
        final var lobby = new Lobby(lobbyId, gameId, hostId, "OUTBOX1", new ArrayList<>());
        final var envelope = EventEnvelope.create(
                "session.LobbyCreated", lobbyId, "Lobby", gameId, 1, new LobbyCreated(lobbyId, hostId, Instant.now()));

        transactionTemplate.executeWithoutResult(_ -> {
            lobbyRepository.save(lobby);
            sessionEventPublisher.publish(envelope);
        });

        final var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%EventEnvelope%'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void publish_onRollback_doesNotStoreEventInOutbox() {
        final var lobbyId = UUID.randomUUID();
        final var gameId = UUID.randomUUID();
        final var hostId = UUID.randomUUID();
        final var lobby = new Lobby(lobbyId, gameId, hostId, "OUTBOX2", new ArrayList<>());
        final var envelope = EventEnvelope.create(
                "session.LobbyCreated", lobbyId, "Lobby", gameId, 1, new LobbyCreated(lobbyId, hostId, Instant.now()));

        transactionTemplate.executeWithoutResult(status -> {
            lobbyRepository.save(lobby);
            sessionEventPublisher.publish(envelope);
            status.setRollbackOnly();
        });

        final var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%EventEnvelope%'", Integer.class);
        assertThat(count).isZero();
    }
}
