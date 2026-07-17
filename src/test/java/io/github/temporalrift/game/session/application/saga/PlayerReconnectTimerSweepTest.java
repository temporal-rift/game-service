package io.github.temporalrift.game.session.application.saga;

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

import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectTimerSweepTest {

    static final Instant NOW = Instant.parse("2099-01-01T00:01:00Z");

    @Mock
    PlayerReconnectSagaStateManager stateManager;

    @Mock
    PlayerReconnectTimeoutProcessor timeoutProcessor;

    PlayerReconnectTimerSweep sweep() {
        return new PlayerReconnectTimerSweep(stateManager, timeoutProcessor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("due grace periods are processed")
    void sweep_processesDueGracePeriods() {
        // given
        var due = state();
        given(stateManager.findGracePeriodDueBy(NOW)).willReturn(List.of(due));

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should().handleTimerExpiry(due.sagaId());
    }

    @Test
    @DisplayName("nothing due — processor untouched")
    void sweep_nothingDue_noProcessing() {
        // given
        given(stateManager.findGracePeriodDueBy(NOW)).willReturn(List.of());

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should(never()).handleTimerExpiry(any());
    }

    @Test
    @DisplayName("one failing saga does not starve the rest of the batch")
    void sweep_failureDoesNotStarveBatch() {
        // given
        var failing = state();
        var healthy = state();
        given(stateManager.findGracePeriodDueBy(NOW)).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("lobby row missing"))
                .given(timeoutProcessor)
                .handleTimerExpiry(failing.sagaId());

        // when
        sweep().sweep();

        // then
        then(timeoutProcessor).should().handleTimerExpiry(healthy.sagaId());
    }

    private static PlayerReconnectSagaState state() {
        return new PlayerReconnectSagaState(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                NOW.minusSeconds(5));
    }
}
