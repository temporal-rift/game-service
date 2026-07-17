package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionRoundTimerRegistryTest {

    @Mock
    ScheduledFuture<?> future;

    @Mock
    ScheduledFuture<?> replacement;

    @Test
    @DisplayName("register ignores null futures")
    void register_nullFuture_ignoresIt() {
        // given
        var registry = new ActionRoundTimerRegistry();
        var sagaId = UUID.randomUUID();

        // when
        registry.register(sagaId, null);
        registry.cancel(sagaId);

        // then
        then(future).should(never()).cancel(false);
    }

    @Test
    @DisplayName("cancel removes and cancels an existing scheduled future")
    void cancel_existingFuture_cancelsIt() {
        // given
        var registry = new ActionRoundTimerRegistry();
        var sagaId = UUID.randomUUID();
        registry.register(sagaId, future);

        // when
        registry.cancel(sagaId);

        // then
        then(future).should().cancel(false);
    }

    @Test
    @DisplayName("register cancels a replaced still-armed timer")
    void register_replacingExistingFuture_cancelsPrevious() {
        // given
        var registry = new ActionRoundTimerRegistry();
        var sagaId = UUID.randomUUID();
        registry.register(sagaId, future);

        // when
        registry.register(sagaId, replacement);

        // then
        then(future).should().cancel(false);
        then(replacement).should(never()).cancel(false);
    }

    @Test
    @DisplayName("remove drops the future without cancelling it")
    void remove_existingFuture_doesNotCancelIt() {
        // given
        var registry = new ActionRoundTimerRegistry();
        var sagaId = UUID.randomUUID();
        registry.register(sagaId, future);

        // when
        registry.remove(sagaId);
        registry.cancel(sagaId);

        // then
        then(future).should(never()).cancel(false);
    }
}
