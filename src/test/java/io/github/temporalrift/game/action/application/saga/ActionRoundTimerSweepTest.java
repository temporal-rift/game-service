package io.github.temporalrift.game.action.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@ExtendWith(MockitoExtension.class)
class ActionRoundTimerSweepTest {

    static final Instant NOW = Instant.parse("2099-01-01T00:01:00Z");

    @Mock
    ActionRoundSagaStateManager stateManager;

    @Mock
    ActionRoundTimeoutProcessor timeoutProcessor;

    ActionRoundTimerSweep sweep() {
        return new ActionRoundTimerSweep(stateManager, timeoutProcessor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("due WAITING sagas are processed")
    void sweep_processesDueWaiting() {
        // given
        var dueWaiting = state(ActionRoundSagaStatus.WAITING);
        given(stateManager.findWaitingDueBy(NOW)).willReturn(List.of(dueWaiting));

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should().handleTimerExpiry(dueWaiting.sagaId());
    }

    @Test
    @DisplayName("nothing due — processor untouched")
    void sweep_nothingDue_noProcessing() {
        // given
        given(stateManager.findWaitingDueBy(NOW)).willReturn(List.of());

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should(never()).handleTimerExpiry(any());
    }

    @Test
    @DisplayName("one failing saga does not starve the rest of the batch")
    void sweep_failureDoesNotStarveBatch() {
        // given
        var failing = state(ActionRoundSagaStatus.WAITING);
        var healthy = state(ActionRoundSagaStatus.WAITING);
        given(stateManager.findWaitingDueBy(NOW)).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("round row missing"))
                .given(timeoutProcessor)
                .handleTimerExpiry(failing.sagaId());

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should().handleTimerExpiry(healthy.sagaId());
    }

    private static ActionRoundSagaState state(ActionRoundSagaStatus status) {
        return new ActionRoundSagaState(
                UUID.randomUUID(), UUID.randomUUID(), 1, 1, status, List.of(), NOW.minusSeconds(5));
    }
}
