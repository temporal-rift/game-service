package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateLobbyCommandHandlerTest {

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    GameRulesPort gameRules;

    @InjectMocks
    CreateLobbyCommandHandler handler;

    @BeforeEach
    void setUp() {
        given(lobbyRepository.findByJoinCode(any())).willReturn(Optional.empty());
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(gameRules.minPlayers()).willReturn(2);
        given(gameRules.maxPlayers()).willReturn(5);
    }

    @Test
    @DisplayName("saves lobby with the host player from the command")
    void execute_savesLobbyWithCorrectHostPlayer() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        assertThat(captor.getValue().hostPlayerId()).isEqualTo(command.playerId());
    }

    @Test
    @DisplayName("marks the first player as host with correct id and name")
    void execute_hostPlayerIsMarkedAsHost() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        var host = captor.getValue().currentPlayers().getFirst();
        assertThat(host.isHost()).isTrue();
        assertThat(host.playerId()).isEqualTo(command.playerId());
        assertThat(host.playerName()).isEqualTo(command.playerName());
    }

    @Test
    @DisplayName("lobbyId and gameId are always different UUIDs")
    void execute_lobbyIdAndGameIdAreDifferent() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(Lobby.class);
        then(lobbyRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isNotEqualTo(captor.getValue().gameId());
    }

    @Test
    @DisplayName("envelope carries pre-assigned gameId as partition key, not lobbyId")
    void execute_envelopeCarriesGameIdNotLobbyId() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.execute(command);

        // then
        var lobbyCaptor = ArgumentCaptor.forClass(Lobby.class);
        var eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(lobbyRepository).should().save(lobbyCaptor.capture());
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().gameId())
                .isEqualTo(lobbyCaptor.getValue().gameId());
        assertThat(eventCaptor.getValue().gameId())
                .isNotEqualTo(lobbyCaptor.getValue().id());
    }

    @Test
    @DisplayName("publishes LobbyCreated with correct lobbyId, hostPlayerId and createdAt")
    void execute_publishesLobbyCreatedPayload() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.execute(command);

        // then
        var lobbyCaptor = ArgumentCaptor.forClass(Lobby.class);
        var eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(lobbyRepository).should().save(lobbyCaptor.capture());
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("session.LobbyCreated");
        var payload = (LobbyCreated) eventCaptor.getValue().payload();
        assertThat(payload.lobbyId()).isEqualTo(lobbyCaptor.getValue().id());
        assertThat(payload.hostPlayerId()).isEqualTo(command.playerId());
        assertThat(payload.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("returns lobbyId, hostPlayerId and a 6-character join code")
    void execute_returnsLobbyIdHostPlayerIdAndJoinCode() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        var result = handler.execute(command);

        // then
        assertThat(result.lobbyId()).isNotNull();
        assertThat(result.hostPlayerId()).isEqualTo(command.playerId());
        assertThat(result.joinCode()).isNotNull().hasSize(6);
    }

    @Test
    @DisplayName("retries join code generation until a unique code is found")
    void execute_joinCodeCollision_retriesUntilUnique() {
        // given
        given(lobbyRepository.findByJoinCode(any()))
                .willReturn(Optional.of(mock(Lobby.class)))
                .willReturn(Optional.empty());
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        var result = handler.execute(command);

        // then
        assertThat(result.joinCode()).isNotNull().hasSize(6);
    }
}
