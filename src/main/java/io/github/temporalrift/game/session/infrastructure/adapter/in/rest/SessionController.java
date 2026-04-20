package io.github.temporalrift.game.session.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.GetGameStateUseCase;
import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.CreateLobbyRequest;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.CreateLobbyResponse;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.GameSummaryResponse;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.JoinLobbyRequest;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.JoinLobbyResponse;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.PlayerInLobby;
import io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.StartGameResponse;
import io.github.temporalrift.game.shared.PlayerPrincipal;

@RestController
class SessionController implements SessionApi {

    private final CreateLobbyUseCase createLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final LeaveLobbyUseCase leaveLobbyUseCase;
    private final StartGameUseCase startGameUseCase;
    private final GetGameStateUseCase getGameStateUseCase;

    SessionController(
            CreateLobbyUseCase createLobbyUseCase,
            JoinLobbyUseCase joinLobbyUseCase,
            LeaveLobbyUseCase leaveLobbyUseCase,
            StartGameUseCase startGameUseCase,
            GetGameStateUseCase getGameStateUseCase) {
        this.createLobbyUseCase = createLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.leaveLobbyUseCase = leaveLobbyUseCase;
        this.startGameUseCase = startGameUseCase;
        this.getGameStateUseCase = getGameStateUseCase;
    }

    @Override
    public ResponseEntity<CreateLobbyResponse> createLobby(CreateLobbyRequest createLobbyRequest) {
        var result = createLobbyUseCase.handle(
                new CreateLobbyUseCase.Command(callerPlayerId(), createLobbyRequest.getPlayerName()));
        return ResponseEntity.status(201)
                .body(new CreateLobbyResponse(result.lobbyId(), result.hostPlayerId(), result.joinCode()));
    }

    @Override
    public ResponseEntity<JoinLobbyResponse> joinLobby(UUID lobbyId, JoinLobbyRequest joinLobbyRequest) {
        var result = joinLobbyUseCase.handle(
                new JoinLobbyUseCase.Command(lobbyId, callerPlayerId(), joinLobbyRequest.getPlayerName()));
        var players = result.currentPlayers().stream()
                .map(p -> new PlayerInLobby(p.playerId(), p.playerName(), p.isHost()))
                .toList();
        return ResponseEntity.ok(new JoinLobbyResponse(result.lobbyId(), result.playerId(), players));
    }

    @Override
    public ResponseEntity<Void> leaveLobby(UUID lobbyId) {
        leaveLobbyUseCase.handle(new LeaveLobbyUseCase.Command(lobbyId, callerPlayerId()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<StartGameResponse> startGame(UUID lobbyId) {
        var result = startGameUseCase.handle(new StartGameUseCase.Command(lobbyId, callerPlayerId()));
        return ResponseEntity.status(202).body(new StartGameResponse(result.gameId()));
    }

    @Override
    public ResponseEntity<GameSummaryResponse> getGame(UUID gameId) {
        var result = getGameStateUseCase.handle(new GetGameStateUseCase.Query(gameId));
        var apiStatus =
                switch (result.status()) {
                    case IN_PROGRESS ->
                        io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.GameStatus.IN_PROGRESS;
                    case ENDED_BY_WIN, ENDED_BY_COLLAPSE, ENDED_BY_STABILIZATION ->
                        io.github.temporalrift.game.session.infrastructure.adapter.in.rest.model.GameStatus.GAME_ENDED;
                };
        return ResponseEntity.ok(new GameSummaryResponse(
                result.gameId(), apiStatus, result.eraNumber(), result.playerCount(), result.cascadedParadoxCount()));
    }

    private UUID callerPlayerId() {
        return ((PlayerPrincipal)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .playerId();
    }
}
