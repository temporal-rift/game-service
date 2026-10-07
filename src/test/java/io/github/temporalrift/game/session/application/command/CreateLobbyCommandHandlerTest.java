package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

@ExtendWith(MockitoExtension.class)
class CreateLobbyCommandHandlerTest {

    static final String JOIN_CODE = "X7K2P9";
    static final Instant NOW = Instant.parse("2026-04-16T12:00:00Z");
    static final UUID LOBBY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID GAME_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionGameRulesPort gameRules;

    @Mock
    JoinCodePort joinCodePort;

    @Mock
    Clock clock;

    @Mock
    ExecutionEntropy entropy;

    @InjectMocks
    CreateLobbyCommandHandler handler;

    @BeforeEach
    void setUp() {
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(entropy.identity(IdentityKind.LOBBY, EntropyCoordinate.none())).willReturn(LOBBY_ID);
        given(entropy.identity(IdentityKind.GAME, EntropyCoordinate.none())).willReturn(GAME_ID);
        given(gameRules.minPlayers()).willReturn(2);
        given(gameRules.maxPlayers()).willReturn(5);
        given(joinCodePort.generate()).willReturn(JOIN_CODE);
        given(clock.instant()).willReturn(NOW);
    }

    @Test
    @DisplayName("saves lobby with the host player from the command")
    void handle_savesLobbyWithCorrectHostPlayer() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        assertThat(captor.getValue().hostPlayerId()).isEqualTo(command.playerId());
    }

    @Test
    @DisplayName("takes the lobby and game identities from the execution entropy")
    void handle_identitiesComeFromExecutionEntropy() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(LOBBY_ID);
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
    }

    @Test
    @DisplayName("marks the first player as host with correct id and name")
    void handle_hostPlayerIsMarkedAsHost() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        var host = captor.getValue().currentPlayers().getFirst();
        assertThat(host.playerId()).isEqualTo(command.playerId());
        assertThat(host.playerName()).isEqualTo(command.playerName());
        assertThat(captor.getValue().hostPlayerId()).isEqualTo(host.playerId());
    }

    @Test
    @DisplayName("lobbyId and gameId are always different UUIDs")
    void handle_lobbyIdAndGameIdAreDifferent() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isNotEqualTo(captor.getValue().gameId());
    }

    @Test
    @DisplayName("returns the join code produced by JoinCodeGenerator")
    void handle_returnsJoinCodeFromGenerator() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        var result = handler.handle(command);

        // then
        assertThat(result.lobbyId()).isNotNull();
        assertThat(result.hostPlayerId()).isEqualTo(command.playerId());
        assertThat(result.joinCode()).isEqualTo(JOIN_CODE);
    }
}
