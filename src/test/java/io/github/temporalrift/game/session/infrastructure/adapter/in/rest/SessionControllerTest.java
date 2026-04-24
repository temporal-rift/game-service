package io.github.temporalrift.game.session.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.temporalrift.game.TestSecurityConfig;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.GetGameStateUseCase;
import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.LobbyAlreadyStartedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyFullException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;
import io.github.temporalrift.game.session.domain.lobby.PlayerNotInLobbyException;
import io.github.temporalrift.game.shared.PlayerPrincipal;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerAuthenticationToken;
import io.github.temporalrift.game.shared.infrastructure.config.SecurityConfig;

@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class SessionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreateLobbyUseCase createLobbyUseCase;

    @MockitoBean
    JoinLobbyUseCase joinLobbyUseCase;

    @MockitoBean
    LeaveLobbyUseCase leaveLobbyUseCase;

    @MockitoBean
    StartGameUseCase startGameUseCase;

    @MockitoBean
    GetGameStateUseCase getGameStateUseCase;

    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final String JOIN_CODE = "ABCDEF";

    private RequestPostProcessor auth() {
        return authentication(new PlayerAuthenticationToken(new PlayerPrincipal(PLAYER_ID)));
    }

    // --- Happy paths ---

    @Test
    @DisplayName("Given valid request, when POST /lobbies, then 201 with lobbyId and joinCode")
    void createLobby_validRequest_returns201() throws Exception {
        // given
        given(createLobbyUseCase.handle(any()))
                .willReturn(new CreateLobbyUseCase.Result(LOBBY_ID, PLAYER_ID, JOIN_CODE));

        // when / then
        mockMvc.perform(post("/lobbies")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName": "Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lobbyId").value(LOBBY_ID.toString()))
                .andExpect(jsonPath("$.hostPlayerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.joinCode").value(JOIN_CODE));
    }

    @Test
    @DisplayName("Given valid request, when POST /lobbies/{lobbyId}/join, then 200 with player list")
    void joinLobby_validRequest_returns200() throws Exception {
        // given
        var summary = new JoinLobbyUseCase.PlayerSummary(PLAYER_ID, "Alice", true);
        given(joinLobbyUseCase.handle(any()))
                .willReturn(new JoinLobbyUseCase.Result(LOBBY_ID, PLAYER_ID, List.of(summary)));

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/join", LOBBY_ID)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName": "Alice"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lobbyId").value(LOBBY_ID.toString()))
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.currentPlayers[0].playerName").value("Alice"));
    }

    @Test
    @DisplayName("Given valid request, when DELETE /lobbies/{lobbyId}/players/me, then 204")
    void leaveLobby_validRequest_returns204() throws Exception {
        // given
        given(leaveLobbyUseCase.handle(any())).willReturn(new LeaveLobbyUseCase.Result());

        // when / then
        mockMvc.perform(delete("/lobbies/{lobbyId}/players/me", LOBBY_ID).with(auth()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Given valid request, when POST /lobbies/{lobbyId}/start, then 202 with gameId")
    void startGame_validRequest_returns202() throws Exception {
        // given
        given(startGameUseCase.handle(any())).willReturn(new StartGameUseCase.Result(GAME_ID));

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/start", LOBBY_ID).with(auth()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()));
    }

    @Test
    @DisplayName("Given active game, when GET /games/{gameId}, then 200 with IN_PROGRESS status")
    void getGame_activeGame_returns200WithStatus() throws Exception {
        // given
        given(getGameStateUseCase.handle(any()))
                .willReturn(new GetGameStateUseCase.Result(GAME_ID, GameStatus.IN_PROGRESS, 2, 3, 1));

        // when / then
        mockMvc.perform(get("/games/{gameId}", GAME_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.eraNumber").value(2))
                .andExpect(jsonPath("$.playerCount").value(3));
    }

    // --- Exception mappings ---

    @Test
    @DisplayName("Given LobbyNotFoundException, then 404")
    void joinLobby_lobbyNotFound_returns404() throws Exception {
        // given
        given(joinLobbyUseCase.handle(any())).willThrow(new LobbyNotFoundException(LOBBY_ID));

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/join", LOBBY_ID)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName": "Alice"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given PlayerNotInLobbyException, then 404")
    void leaveLobby_playerNotInLobby_returns404() throws Exception {
        // given
        given(leaveLobbyUseCase.handle(any())).willThrow(new PlayerNotInLobbyException(PLAYER_ID));

        // when / then
        mockMvc.perform(delete("/lobbies/{lobbyId}/players/me", LOBBY_ID).with(auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given LobbyAlreadyStartedException, then 409")
    void joinLobby_alreadyStarted_returns409() throws Exception {
        // given
        given(joinLobbyUseCase.handle(any())).willThrow(new LobbyAlreadyStartedException());

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/join", LOBBY_ID)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName": "Alice"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Given LobbyFullException, then 422")
    void joinLobby_lobbyFull_returns422() throws Exception {
        // given
        given(joinLobbyUseCase.handle(any())).willThrow(new LobbyFullException());

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/join", LOBBY_ID)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName": "Alice"}
                                """))
                .andExpect(status().is(422));
    }

    @Test
    @DisplayName("Given NotEnoughPlayersException, then 422")
    void startGame_notEnoughPlayers_returns422() throws Exception {
        // given
        given(startGameUseCase.handle(any())).willThrow(new NotEnoughPlayersException());

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/start", LOBBY_ID).with(auth())).andExpect(status().is(422));
    }

    @Test
    @DisplayName("Given NotLobbyHostException, then 403")
    void startGame_notHost_returns403() throws Exception {
        // given
        given(startGameUseCase.handle(any())).willThrow(new NotLobbyHostException());

        // when / then
        mockMvc.perform(post("/lobbies/{lobbyId}/start", LOBBY_ID).with(auth())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Given GameNotFoundException, then 404")
    void getGame_notFound_returns404() throws Exception {
        // given
        given(getGameStateUseCase.handle(any())).willThrow(new GameNotFoundException(GAME_ID));

        // when / then
        mockMvc.perform(get("/games/{gameId}", GAME_ID).with(auth())).andExpect(status().isNotFound());
    }
}
