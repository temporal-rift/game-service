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

import io.github.temporalrift.game.action.application.port.in.SubmitParadoxResolutionUseCase;

@ExtendWith(MockitoExtension.class)
class SubmitParadoxResolutionCommandHandlerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final int ERA = 3;
    private static final SubmitParadoxResolutionUseCase.Result RESULT =
            new SubmitParadoxResolutionUseCase.Result(GAME_ID, ERA, PLAYER_ID);

    @Mock
    PlayParadoxResolutionCardCommandHandler playCard;

    @Mock
    PassParadoxResolutionCommandHandler pass;

    @InjectMocks
    SubmitParadoxResolutionCommandHandler handler;

    @Test
    void card_isHandledByTheCardRules() {
        var command = new SubmitParadoxResolutionUseCase.PlayCard(
                GAME_ID, ERA, PLAYER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(playCard.handle(command)).willReturn(RESULT);

        assertThat(handler.handle(command)).isEqualTo(RESULT);
        then(pass).shouldHaveNoInteractions();
    }

    @Test
    void pass_isHandledByThePassRules() {
        var command = new SubmitParadoxResolutionUseCase.Pass(GAME_ID, ERA, PLAYER_ID);
        given(pass.handle(command)).willReturn(RESULT);

        assertThat(handler.handle(command)).isEqualTo(RESULT);
        then(playCard).shouldHaveNoInteractions();
    }
}
