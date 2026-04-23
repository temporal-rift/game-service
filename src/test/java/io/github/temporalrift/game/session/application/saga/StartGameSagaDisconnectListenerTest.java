package io.github.temporalrift.game.session.application.saga;

import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartGameSagaDisconnectListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    StartGameSagaCompensator compensator;

    @InjectMocks
    StartGameSagaDisconnectListener listener;

    @Test
    @DisplayName("player disconnected event received — delegates cancellation to compensator with gameId")
    void onPlayerDisconnected_delegatesToCompensatorWithGameId() {
        // given
        var event = new PlayerDisconnectedApplicationEvent(GAME_ID, PLAYER_ID);

        // when
        listener.onPlayerDisconnected(event);

        // then
        then(compensator).should().cancel(GAME_ID);
    }
}
