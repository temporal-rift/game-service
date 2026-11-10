package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class ActionRoundRequestsTest {

    @Mock
    GameRepository gameRepository;

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    Game game;

    @Mock
    Lobby lobby;

    @InjectMocks
    ActionRoundRequests requests;

    @Test
    void open_listsOnlyParticipantsWhoHaveNotAbandoned() {
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var stays = UUID.randomUUID();
        var leaves = UUID.randomUUID();
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));
        given(game.lobbyId()).willReturn(lobbyId);
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        var contender = org.mockito.Mockito.mock(LobbyPlayer.class);
        given(contender.playerId()).willReturn(stays);
        given(lobby.contenders()).willReturn(List.of(contender));

        var event = requests.open(gameId, 2, 3, List.of(stays, leaves));

        assertThat(event.playerIds()).containsExactly(stays, leaves);
        assertThat(event.activePlayerIds()).containsExactly(stays);
        assertThat(event.eraNumber()).isEqualTo(2);
        assertThat(event.roundNumber()).isEqualTo(3);
    }
}
