package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;

import io.github.temporalrift.game.GameServiceApplication;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@ExtendWith(MockitoExtension.class)
class ActionRoundSagaRecoveryTest {

    static final UUID SAGA_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();

    @Mock
    ActionRoundSagaStateManager stateManager;

    @Mock
    ActionRoundTimerScheduler timerScheduler;

    @Mock
    ActionRoundTimeoutProcessor timeoutProcessor;

    @Test
    @DisplayName("startup reschedules waiting sagas whose timer has not expired")
    void onApplicationEvent_waitingSagaInFuture_reschedules() {
        // given
        var recovery = new ActionRoundSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                1,
                2,
                ActionRoundSagaStatus.WAITING,
                List.of(UUID.randomUUID()),
                Instant.now().plusSeconds(30));
        given(stateManager.findAllWaiting()).willReturn(List.of(state));
        given(stateManager.findAllClosing()).willReturn(List.of());

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timerScheduler).should().reschedule(SAGA_ID, state.timerExpiresAt());
    }

    @Test
    @DisplayName("startup immediately expires waiting sagas whose timer already elapsed")
    void onApplicationEvent_waitingSagaExpired_processesImmediately() {
        // given
        var recovery = new ActionRoundSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                1,
                2,
                ActionRoundSagaStatus.WAITING,
                List.of(UUID.randomUUID()),
                Instant.now().minusSeconds(5));
        given(stateManager.findAllWaiting()).willReturn(List.of(state));
        given(stateManager.findAllClosing()).willReturn(List.of());

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timeoutProcessor).should().handleTimerExpiry(SAGA_ID);
    }

    @Test
    @DisplayName("startup resumes all closing sagas through the timeout processor")
    void onApplicationEvent_closingSagas_resumeThroughTimeoutProcessor() {
        // given
        var recovery = new ActionRoundSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                1,
                2,
                ActionRoundSagaStatus.CLOSING,
                List.of(),
                Instant.now().plusSeconds(30));
        given(stateManager.findAllWaiting()).willReturn(List.of());
        given(stateManager.findAllClosing()).willReturn(List.of(state));

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timeoutProcessor).should().handleTimerExpiry(SAGA_ID);
    }

    static class SpringApplicationStub extends org.springframework.boot.SpringApplication {
        SpringApplicationStub() {
            super(GameServiceApplication.class);
        }
    }
}
