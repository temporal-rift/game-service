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

import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

@ExtendWith(MockitoExtension.class)
class ScoringReadProjectionEventListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    ScoringGameVisibilityRepository visibilityRepository;

    @Mock
    ScoringPlayerRepository playerRepository;

    @InjectMocks
    ScoringReadProjectionEventListener listener;

    @Test
    @DisplayName("PlayerJoinedLobby — projects the player name keyed by gameId")
    void onPlayerJoinedLobby_upsertsName() {
        listener.onPlayerJoinedLobby(new PlayerJoinedLobby(GAME_ID, LOBBY_ID, PLAYER_ID, "Ada"));

        then(playerRepository).should().upsertPlayerName(GAME_ID, PLAYER_ID, "Ada");
    }

    @Test
    @DisplayName("FactionRevealed — marks factions revealed for the game")
    void onFactionRevealed_marksRevealed() {
        listener.onFactionRevealed(
                new FactionRevealed(GAME_ID, List.of(new FactionRevealed.PlayerFactionResult(PLAYER_ID, "ERASERS"))));

        then(visibilityRepository).should().markFactionsRevealed(GAME_ID);
    }
}
