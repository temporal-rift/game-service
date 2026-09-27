package io.github.temporalrift.game.scoring.application.command;

import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.shared.domain.event.FactionRevealed;

@ExtendWith(MockitoExtension.class)
class FactionRevealCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    ScoringGameVisibilityRepository visibilityRepository;

    @InjectMocks
    FactionRevealCommandHandler handler;

    @Test
    @DisplayName("reveal — makes factions visible without applying any score")
    void reveal_marksFactionsRevealed() {
        var event =
                new FactionRevealed(GAME_ID, List.of(new FactionRevealed.PlayerFactionResult(PLAYER_ID, "ERASERS")));

        handler.reveal(event);

        verify(visibilityRepository).markFactionsRevealed(GAME_ID);
    }
}
