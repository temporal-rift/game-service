package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionRoundTimeoutProcessorTest {

    @Mock
    ActionRoundSagaImpl saga;

    @Test
    @DisplayName("handleTimerExpiry delegates to the saga")
    void handleTimerExpiry_delegatesToSaga() {
        // given
        var processor = new ActionRoundTimeoutProcessor(saga);
        var sagaId = UUID.randomUUID();

        // when
        processor.handleTimerExpiry(sagaId);

        // then
        then(saga).should().handleTimerExpiry(sagaId);
    }
}
