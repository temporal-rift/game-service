package io.github.temporalrift.game.scoring.application.command;

import static org.mockito.Mockito.inOrder;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.shared.FactionRevealed;

@ExtendWith(MockitoExtension.class)
class FactionRevealCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    AwardUnidentifiedFactionScores awardUnidentifiedFactionScores;

    @Mock
    ScoringGameVisibilityRepository visibilityRepository;

    @InjectMocks
    FactionRevealCommandHandler handler;

    @Test
    @DisplayName("reveal — awards end-game bonuses before making factions visible")
    void reveal_awardsBeforeMarkingRevealed() {
        var event =
                new FactionRevealed(GAME_ID, List.of(new FactionRevealed.PlayerFactionResult(PLAYER_ID, "ERASERS")));

        handler.reveal(event);

        var inOrder = inOrder(awardUnidentifiedFactionScores, visibilityRepository);
        inOrder.verify(awardUnidentifiedFactionScores).award(event);
        inOrder.verify(visibilityRepository).markFactionsRevealed(GAME_ID);
    }
}
