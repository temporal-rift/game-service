package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.port.out.EndGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaStatus;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@ExtendWith(MockitoExtension.class)
class EndGameSagaStateManagerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(PLAYER_1);
    static final EndGameSagaState RUNNING_STATE =
            new EndGameSagaState(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, EndGameSagaStatus.RUNNING, PLAYER_IDS);

    @Mock
    EndGameSagaRepository endGameSagaRepository;

    @InjectMocks
    EndGameSagaStateManager stateManager;

    @Test
    @DisplayName("claimIfAbsent — claims a RUNNING state for the given trigger and players")
    void claimIfAbsent_delegatesAndReturnsResult() {
        // given
        given(endGameSagaRepository.claimIfAbsent(
                        argThat(state -> state.gameId().equals(GAME_ID)
                                && state.triggerType() == EndGameTrigger.WIN_CONDITION_MET
                                && state.status() == EndGameSagaStatus.RUNNING
                                && state.playerIds().equals(PLAYER_IDS))))
                .willReturn(true);

        // when
        var result = stateManager.claimIfAbsent(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_IDS);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("claimIfAbsent — returns false when the repository reports the claim already exists")
    void claimIfAbsent_alreadyClaimed_returnsFalse() {
        // given
        given(endGameSagaRepository.claimIfAbsent(any())).willReturn(false);

        // when
        var result = stateManager.claimIfAbsent(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_IDS);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("complete — updates existing saga state to COMPLETED")
    void complete_updatesExistingStateToCompleted() {
        // given
        given(endGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));

        // when
        stateManager.complete(GAME_ID);

        // then
        then(endGameSagaRepository)
                .should()
                .save(argThat(s -> s.gameId().equals(GAME_ID) && s.status() == EndGameSagaStatus.COMPLETED));
    }

    @Test
    @DisplayName("complete — no saga found for gameId, takes no action")
    void complete_sagaNotFound_takesNoAction() {
        // given
        given(endGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        stateManager.complete(GAME_ID);

        // then
        then(endGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(endGameSagaRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("compensate — updates existing saga state to COMPENSATING")
    void compensate_updatesExistingStateToCompensating() {
        // given
        given(endGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));

        // when
        stateManager.compensate(GAME_ID);

        // then
        then(endGameSagaRepository)
                .should()
                .save(argThat(s -> s.gameId().equals(GAME_ID) && s.status() == EndGameSagaStatus.COMPENSATING));
    }

    @Test
    @DisplayName("compensate — no saga found for gameId, takes no action")
    void compensate_sagaNotFound_takesNoAction() {
        // given
        given(endGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        stateManager.compensate(GAME_ID);

        // then
        then(endGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(endGameSagaRepository).shouldHaveNoMoreInteractions();
    }
}
