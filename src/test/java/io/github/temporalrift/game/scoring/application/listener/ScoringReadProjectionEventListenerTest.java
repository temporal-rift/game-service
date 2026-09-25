package io.github.temporalrift.game.scoring.application.listener;

import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.shared.domain.event.GameStarted;

@ExtendWith(MockitoExtension.class)
class ScoringReadProjectionEventListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    ScoringPlayerRepository playerRepository;

    @InjectMocks
    ScoringReadProjectionEventListener listener;

    @Test
    @DisplayName("GameStarted — projects every roster name keyed by gameId")
    void onGameStarted_upsertsEveryRosterName() {
        var other = UUID.randomUUID();

        listener.onGameStarted(new GameStarted(
                GAME_ID,
                LOBBY_ID,
                List.of(new GameStarted.Player(PLAYER_ID, "Ada"), new GameStarted.Player(other, "Ben")),
                2,
                30));

        then(playerRepository).should().upsertPlayerName(GAME_ID, PLAYER_ID, "Ada");
        then(playerRepository).should().upsertPlayerName(GAME_ID, other, "Ben");
    }
}
