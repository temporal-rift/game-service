package io.github.temporalrift.game.session.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.GetGameStateUseCase;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class GetGameStateQueryHandlerTest {

    @Mock
    GameRepository gameRepository;

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    Game game;

    @Mock
    Lobby lobby;

    @InjectMocks
    GetGameStateQueryHandler handler;

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();

    @Test
    @DisplayName("game exists — returns result with all fields populated from game and lobby")
    void handle_gameExists_returnsResultWithAllFields() {
        // given
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(game.lobbyId()).willReturn(LOBBY_ID);
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(game.id()).willReturn(GAME_ID);
        given(game.status()).willReturn(GameStatus.IN_PROGRESS);
        given(game.eraCounter()).willReturn(2);
        given(game.cascadedParadoxCounter()).willReturn(1);
        var players = List.of(
                new LobbyPlayer(UUID.randomUUID(), "Alice", null, Instant.now(), true),
                new LobbyPlayer(UUID.randomUUID(), "Bob", null, Instant.now(), true),
                new LobbyPlayer(UUID.randomUUID(), "Carol", null, Instant.now(), true));
        given(lobby.currentPlayers()).willReturn(players);
        var query = new GetGameStateUseCase.Query(GAME_ID);

        // when
        var result = handler.handle(query);

        // then
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(result.eraNumber()).isEqualTo(2);
        assertThat(result.playerCount()).isEqualTo(3);
        assertThat(result.cascadedParadoxCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("game not found — throws GameNotFoundException without touching lobby repository")
    void handle_gameNotFound_throwsGameNotFoundException() {
        // given
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.empty());
        var query = new GetGameStateUseCase.Query(GAME_ID);

        // when / then
        assertThatExceptionOfType(GameNotFoundException.class).isThrownBy(() -> handler.handle(query));
        then(lobbyRepository).shouldHaveNoInteractions();
    }
}
