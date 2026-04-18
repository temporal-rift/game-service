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

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateLobbyCommandHandlerTest {

    static final String JOIN_CODE = "X7K2P9";
    static final Instant NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    GameRulesPort gameRules;

    @Mock
    JoinCodePort joinCodePort;

    @Mock
    Clock clock;

    @InjectMocks
    CreateLobbyCommandHandler handler;

    @BeforeEach
    void setUp() {
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
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
    @DisplayName("envelope carries pre-assigned gameId as partition key, not lobbyId")
    void handle_envelopeCarriesGameIdNotLobbyId() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

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
    void handle_publishesLobbyCreatedPayload() {
        // given
        var command = new CreateLobbyUseCase.Command(UUID.randomUUID(), "Alice");

        // when
        handler.handle(command);

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
