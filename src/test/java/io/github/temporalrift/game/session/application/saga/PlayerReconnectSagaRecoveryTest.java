package io.github.temporalrift.game.session.application.saga;

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
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectSagaRecoveryTest {

    static final UUID SAGA_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    PlayerReconnectSagaStateManager stateManager;

    @Mock
    PlayerReconnectTimerScheduler timerScheduler;

    @Mock
    PlayerReconnectTimeoutProcessor timeoutProcessor;

    @Test
    @DisplayName("startup does nothing when there are no in-flight grace periods")
    void onApplicationEvent_noInflightSagas_noop() {
        // given
        var recovery = new PlayerReconnectSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        given(stateManager.findAllInGracePeriod()).willReturn(List.of());

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timerScheduler).shouldHaveNoInteractions();
        then(timeoutProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("startup immediately processes expired grace periods")
    void onApplicationEvent_expiredGracePeriod_processesImmediately() {
        // given
        var recovery = new PlayerReconnectSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        var state = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.parse("2000-01-01T00:00:00Z"));
        given(stateManager.findAllInGracePeriod()).willReturn(List.of(state));

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timeoutProcessor).should().handleTimerExpiry(SAGA_ID);
    }

    @Test
    @DisplayName("startup reschedules non-expired grace periods")
    void onApplicationEvent_futureGracePeriod_reschedules() {
        // given
        var recovery = new PlayerReconnectSagaRecovery(stateManager, timerScheduler, timeoutProcessor);
        var state = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.parse("2099-01-01T00:00:20Z"));
        given(stateManager.findAllInGracePeriod()).willReturn(List.of(state));

        // when
        recovery.onApplicationEvent(new ApplicationStartedEvent(
                new SpringApplicationStub(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));

        // then
        then(timerScheduler).should().reschedule(SAGA_ID, state.graceExpiresAt());
    }

    static class SpringApplicationStub extends org.springframework.boot.SpringApplication {
        SpringApplicationStub() {
            super(GameServiceApplication.class);
        }
    }
}
