package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

/**
 * Proves the compensator can actually observe and act on a failed start attempt against a real database.
 * Both {@code start()} and {@code compensate()} run in their own transactions ({@code REQUIRES_NEW}), so
 * this is only observable with real transaction boundaries — a Mockito-based unit test cannot catch a
 * transaction visibility bug because it never has a second, isolated database connection.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StartGameSagaCompensationIT {

    @Autowired
    StartGameSaga startGameSaga;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    StartGameSagaRepository startGameSagaRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FutureEventCatalogPort futureEventCatalog;

    @Test
    void start_failsPartway_compensatorPersistsFailureAndPublishesGameStartFailed() {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var host = new LobbyPlayer(hostPlayerId, "Host", null, Instant.now(clock), true);
        var second = new LobbyPlayer(UUID.randomUUID(), "Second", null, Instant.now(clock), true);
        var lobby = new Lobby(
                lobbyId,
                gameId,
                hostPlayerId,
                new ArrayList<>(List.of(host, second)),
                new LobbyConfig("COMP01", 2, 5, clock));
        transactionTemplate.executeWithoutResult(_ -> lobbyRepository.save(lobby));

        // Fails deep inside the try block, well after initRunning has already (transactionally) run —
        // this is exactly the scenario the compensator exists for.
        given(futureEventCatalog.allEventIds()).willThrow(new RuntimeException("catalog unavailable"));

        var outboxRowsBeforeStart = gameStartFailedOutboxRows();

        assertThatException().isThrownBy(() -> startGameSaga.start(lobbyId, hostPlayerId));

        // start()'s own transaction rolled back: the lobby must still be WAITING, never STARTED.
        var reloadedLobby = lobbyRepository.findById(lobbyId).orElseThrow();
        assertThat(reloadedLobby.status()).isEqualTo(LobbyStatus.WAITING);

        // The compensator, in its own separate transaction, must have durably recorded the failure —
        // under the pre-fix code this row would not exist at all, because compensate() read state
        // written by start()'s (rolled-back) transaction and silently found nothing.
        var failedState = startGameSagaRepository.findByGameId(gameId).orElseThrow();
        assertThat(failedState.status()).isEqualTo(StartGameSagaStatus.FAILED);
        assertThat(failedState.lobbyId()).isEqualTo(lobbyId);

        assertThat(gameStartFailedOutboxRows()).isEqualTo(outboxRowsBeforeStart + 1);
    }

    private Integer gameStartFailedOutboxRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication "
                        + "WHERE serialized_event LIKE '%Sessionpublish-game-start-failed-out%'",
                Integer.class);
    }
}
