package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@ExtendWith(MockitoExtension.class)
class ActionRoundSagaStateManagerTest {

    static final UUID SAGA_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final int ERA_NUMBER = 1;
    static final int ROUND_NUMBER = 2;
    static final Instant TIMER_EXPIRES_AT = Instant.parse("2099-01-01T00:00:30Z");

    @Mock
    ActionRoundSagaRepository repository;

    @InjectMocks
    ActionRoundSagaStateManager stateManager;

    @Test
    @DisplayName("initWaiting saves a waiting state with the provided identifiers and players")
    void initWaiting_savesWaitingState() {
        given(repository.save(any(ActionRoundSagaState.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        stateManager.initWaiting(
                SAGA_ID, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_1, PLAYER_2), TIMER_EXPIRES_AT);

        // then
        var captor = ArgumentCaptor.forClass(ActionRoundSagaState.class);
        then(repository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.sagaId()).isEqualTo(SAGA_ID);
        assertThat(saved.gameId()).isEqualTo(GAME_ID);
        assertThat(saved.status()).isEqualTo(ActionRoundSagaStatus.WAITING);
        assertThat(saved.pendingPlayerIds()).containsExactly(PLAYER_1, PLAYER_2);
    }

    @Test
    @DisplayName("markSubmitted removes the player from the pending list when saga is waiting")
    void markSubmitted_waitingSaga_updatesPendingPlayers() {
        // given
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                ActionRoundSagaStatus.WAITING,
                List.of(PLAYER_1, PLAYER_2),
                TIMER_EXPIRES_AT);
        given(repository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                .willReturn(Optional.of(state));
        given(repository.save(any(ActionRoundSagaState.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        var updated = stateManager
                .markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1)
                .orElseThrow();

        // then
        then(repository).should().save(argThat(saved -> saved.pendingPlayerIds().equals(List.of(PLAYER_2))));
        assertThat(updated.pendingPlayerIds()).containsExactly(PLAYER_2);
    }

    @Test
    @DisplayName("markSubmitted returns current state without saving when saga is not waiting")
    void markSubmitted_nonWaitingSaga_returnsStateWithoutSaving() {
        // given
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                ActionRoundSagaStatus.CLOSING,
                List.of(PLAYER_1),
                TIMER_EXPIRES_AT);
        given(repository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                .willReturn(Optional.of(state));

        // when
        var updated = stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

        // then
        assertThat(updated).contains(state);
        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("markSubmitted returns empty without saving when the saga row is missing")
    void markSubmitted_missingSaga_returnsEmptyWithoutSaving() {
        // given
        given(repository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                .willReturn(Optional.empty());

        // when
        var updated = stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

        // then
        assertThat(updated).isEmpty();
        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("markClosing saves closing status only when state exists and is still waiting")
    void markClosing_waitingSaga_savesClosingStatus() {
        // given
        var state = new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                ActionRoundSagaStatus.WAITING,
                List.of(PLAYER_1),
                TIMER_EXPIRES_AT);
        given(repository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                .willReturn(Optional.of(state));

        // when
        stateManager.markClosing(GAME_ID, ERA_NUMBER, ROUND_NUMBER);

        // then
        then(repository).should().save(argThat(saved -> saved.status() == ActionRoundSagaStatus.CLOSING));
    }

    @Test
    @DisplayName("complete saves completed status when the saga exists and is not already completed")
    void complete_existingSaga_savesCompletedStatus() {
        // given
        var state = new ActionRoundSagaState(
                SAGA_ID, GAME_ID, ERA_NUMBER, ROUND_NUMBER, ActionRoundSagaStatus.CLOSING, List.of(), TIMER_EXPIRES_AT);
        given(repository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                .willReturn(Optional.of(state));

        // when
        stateManager.complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);

        // then
        then(repository).should().save(argThat(saved -> saved.status() == ActionRoundSagaStatus.COMPLETED));
    }

    @Test
    @DisplayName("findBySagaId and collection accessors delegate directly to the repository")
    void delegationMethods_delegateToRepository() {
        // given
        var waiting = List.of(new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                ActionRoundSagaStatus.WAITING,
                List.of(PLAYER_1),
                TIMER_EXPIRES_AT));
        var closing = List.of(new ActionRoundSagaState(
                SAGA_ID,
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                ActionRoundSagaStatus.CLOSING,
                List.of(),
                TIMER_EXPIRES_AT));
        given(repository.findBySagaId(SAGA_ID)).willReturn(waiting.stream().findFirst());
        given(repository.findWaitingDueBy(TIMER_EXPIRES_AT)).willReturn(waiting);
        given(repository.findAllClosing()).willReturn(closing);

        // when
        var bySagaId = stateManager.findBySagaId(SAGA_ID);
        var waitingStates = stateManager.findWaitingDueBy(TIMER_EXPIRES_AT);
        var closingStates = stateManager.findAllClosing();

        // then
        assertThat(bySagaId).contains(waiting.getFirst());
        assertThat(waitingStates).isEqualTo(waiting);
        assertThat(closingStates).isEqualTo(closing);
    }
}
