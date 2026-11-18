package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.port.in.SubmitRoundActionUseCase;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@ExtendWith(MockitoExtension.class)
class SubmitRoundActionCommandHandlerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final int ROUND = 1;
    private static final SubmitRoundActionUseCase.Result RESULT =
            new SubmitRoundActionUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false);

    @Mock
    PlayCardCommandHandler playCard;

    @Mock
    PlaySpecialActionCommandHandler playSpecial;

    @Mock
    PassActionRoundCommandHandler pass;

    @InjectMocks
    SubmitRoundActionCommandHandler handler;

    @Test
    void card_isHandledByTheCardRules() {
        var command = new SubmitRoundActionUseCase.PlayCard(
                GAME_ID, ERA, ROUND, PLAYER_ID, UUID.randomUUID(), UUID.randomUUID(), null, null, null);
        given(playCard.handle(command)).willReturn(RESULT);

        assertThat(handler.handle(command)).isEqualTo(RESULT);
        then(playSpecial).shouldHaveNoInteractions();
        then(pass).shouldHaveNoInteractions();
    }

    @Test
    void special_isHandledByTheSpecialRules() {
        var command = new SubmitRoundActionUseCase.PlaySpecial(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, null, null, UUID.randomUUID());
        given(playSpecial.handle(command)).willReturn(RESULT);

        assertThat(handler.handle(command)).isEqualTo(RESULT);
        then(playCard).shouldHaveNoInteractions();
        then(pass).shouldHaveNoInteractions();
    }

    @Test
    void pass_isHandledByThePassRules() {
        var command = new SubmitRoundActionUseCase.Pass(GAME_ID, ERA, ROUND, PLAYER_ID);
        given(pass.handle(command)).willReturn(RESULT);

        assertThat(handler.handle(command)).isEqualTo(RESULT);
        then(playCard).shouldHaveNoInteractions();
        then(playSpecial).shouldHaveNoInteractions();
    }
}
