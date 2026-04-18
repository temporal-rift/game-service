package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SessionPersistenceIT {

    static final int MAX_ERAS = 5;
    static final int MAX_CASCADED_PARADOXES = 3;
    static final int EVENTS_PER_ERA = 3;
    static final int EVENT_COUNT = 15;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    Clock clock;

    @Test
    void lobby_save_and_findById_roundTripsAllFields() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var player1 = new LobbyPlayer(hostPlayerId, "Alice", Faction.ERASERS, Instant.now(), true);
        var player2 = new LobbyPlayer(UUID.randomUUID(), "Bob", Faction.PROPHETS, Instant.now(), true);
        var players = new ArrayList<LobbyPlayer>();
        players.add(player1);
        players.add(player2);

        var lobby = new Lobby(id, gameId, hostPlayerId, "SAVE01", players, 2, 5, clock);
        lobbyRepository.save(lobby);

        var loaded = lobbyRepository.findById(id);

        assertThat(loaded).isPresent();
        var result = loaded.get();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.gameId()).isEqualTo(gameId);
        assertThat(result.hostPlayerId()).isEqualTo(hostPlayerId);
        assertThat(result.joinCode()).isEqualTo("SAVE01");
        assertThat(result.status()).isEqualTo(LobbyStatus.WAITING);
        assertThat(result.currentPlayers()).hasSize(2);

        var loadedPlayer = result.currentPlayers().stream()
                .filter(p -> p.playerId().equals(hostPlayerId))
                .findFirst();
        assertThat(loadedPlayer).isPresent();
        assertThat(loadedPlayer.get().playerName()).isEqualTo("Alice");
        assertThat(result.hostPlayerId()).isEqualTo(hostPlayerId);
        assertThat(loadedPlayer.get().faction()).isEqualTo(Faction.ERASERS);
    }

    @Test
    void lobby_findByJoinCode_returnsCorrectLobby() {
        var id = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var lobby = new Lobby(id, UUID.randomUUID(), hostPlayerId, "FIND01", new ArrayList<>(), 2, 5, clock);
        lobbyRepository.save(lobby);

        var found = lobbyRepository.findByJoinCode("FIND01");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(id);
        assertThat(found.get().joinCode()).isEqualTo("FIND01");
    }

    @Test
    void lobby_findByJoinCode_unknownCode_returnsEmpty() {
        assertThat(lobbyRepository.findByJoinCode("XXXXX")).isEmpty();
    }

    @Test
    void game_save_and_findById_roundTripsAllFields() {
        var id = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var eventIds = IntStream.range(0, EVENT_COUNT)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        var game = new Game(id, lobbyId, eventIds);
        gameRepository.save(game);

        var loaded = gameRepository.findById(id);

        assertThat(loaded).isPresent();
        var result = loaded.get();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.lobbyId()).isEqualTo(lobbyId);
        assertThat(result.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(result.eraCounter()).isZero();
        assertThat(result.cascadedParadoxCounter()).isZero();
        assertThat(result.availableEventIds()).containsExactlyElementsOf(eventIds);
    }

    @Test
    void game_save_afterEraStarted_persistsUpdatedState() {
        var id = UUID.randomUUID();
        var eventIds = IntStream.range(0, EVENT_COUNT)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        var game = new Game(id, UUID.randomUUID(), eventIds);
        game.startEra(0, EVENTS_PER_ERA);
        gameRepository.save(game);

        var loaded = gameRepository.findById(id).orElseThrow();

        assertThat(loaded.eraCounter()).isEqualTo(1);
        assertThat(loaded.availableEventIds()).hasSize(EVENT_COUNT - EVENTS_PER_ERA);
        assertThat(loaded.availableEventIds()).containsExactlyElementsOf(game.availableEventIds());
    }
}
