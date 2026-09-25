package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

/** Proves the started game's roster reaches the scoring module in-process, not only the Kafka outbox. */
@GameServiceIntegrationTest
class StartGameSagaRosterIT {

    @Autowired
    StartGameSaga startGameSaga;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FutureEventCatalogPort futureEventCatalog;

    @Test
    void start_scoringProjectsEveryRosterName() {
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var secondPlayerId = UUID.randomUUID();
        var lobby = new Lobby(
                UUID.randomUUID(),
                gameId,
                hostPlayerId,
                new ArrayList<>(List.of(
                        new LobbyPlayer(hostPlayerId, "Ada", null, Instant.now(clock), true),
                        new LobbyPlayer(secondPlayerId, "Ben", null, Instant.now(clock), true))),
                new LobbyConfig("ROSTER", 2, 5, clock));
        transactionTemplate.executeWithoutResult(_ -> lobbyRepository.save(lobby));
        given(futureEventCatalog.allEventIds())
                .willReturn(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        startGameSaga.start(lobby.id(), hostPlayerId);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForList(
                                "SELECT player_name FROM scoring_player WHERE game_id = ? ORDER BY player_name",
                                String.class,
                                gameId))
                        .containsExactly("Ada", "Ben"));
    }
}
