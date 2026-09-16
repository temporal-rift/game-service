package io.github.temporalrift.game.session.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.GetLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyAccessDeniedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class GetLobbyQueryHandlerTest {

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    Lobby lobby;

    @InjectMocks
    GetLobbyQueryHandler handler;

    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID CALLER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    @Test
    @DisplayName("member — returns lobby identity, host, status and members with host flags")
    void handle_member_returnsFullResult() {
        // given
        var players = List.of(
                new LobbyPlayer(CALLER, "Alice", null, java.time.Instant.parse("2026-01-01T00:00:00Z"), true),
                new LobbyPlayer(OTHER, "Bob", null, java.time.Instant.parse("2026-01-01T00:00:00Z"), true));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.currentPlayers()).willReturn(players);
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.gameId()).willReturn(GAME_ID);
        given(lobby.hostPlayerId()).willReturn(CALLER);
        given(lobby.status()).willReturn(LobbyStatus.WAITING);

        // when
        var result = handler.handle(new GetLobbyUseCase.Query(LOBBY_ID, CALLER));

        // then
        assertThat(result.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.hostPlayerId()).isEqualTo(CALLER);
        assertThat(result.status()).isEqualTo(LobbyStatus.WAITING);
        assertThat(result.members())
                .containsExactly(
                        new GetLobbyUseCase.MemberSummary(CALLER, "Alice", true),
                        new GetLobbyUseCase.MemberSummary(OTHER, "Bob", false));
    }

    @Test
    @DisplayName("unknown lobby — throws LobbyNotFoundException")
    void handle_unknownLobby_throwsLobbyNotFoundException() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> handler.handle(new GetLobbyUseCase.Query(LOBBY_ID, CALLER)))
                .isInstanceOf(LobbyNotFoundException.class);
    }

    @Test
    @DisplayName("non-member — throws LobbyAccessDeniedException")
    void handle_nonMember_throwsLobbyAccessDeniedException() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.currentPlayers())
                .willReturn(List.of(
                        new LobbyPlayer(OTHER, "Bob", null, java.time.Instant.parse("2026-01-01T00:00:00Z"), true)));

        // when / then
        assertThatThrownBy(() -> handler.handle(new GetLobbyUseCase.Query(LOBBY_ID, CALLER)))
                .isInstanceOf(LobbyAccessDeniedException.class);
    }
}
