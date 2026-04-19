package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class LeaveLobbyCommandHandlerTest {

    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    Lobby lobby;

    @InjectMocks
    LeaveLobbyCommandHandler handler;

    @Test
    @DisplayName("calls leave on the lobby aggregate with the player id from the command")
    void handle_callsLeaveOnLobby() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        then(lobby).should().leave(PLAYER_ID);
    }

    @Test
    @DisplayName("saves lobby after leave")
    void handle_savesLobbyAfterLeave() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        then(lobbyRepository).should().save(lobby);
    }

    @Test
    @DisplayName("lobby not found — throws LobbyNotFoundException without touching the lobby")
    void handle_lobbyNotFound_throwsLobbyNotFoundException() {
        // given
        given(lobbyRepository.findById(any())).willReturn(Optional.empty());
        var command = new LeaveLobbyUseCase.Command(UUID.randomUUID(), PLAYER_ID);

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class).isThrownBy(() -> handler.handle(command));
        then(lobby).shouldHaveNoInteractions();
    }
}
