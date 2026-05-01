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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@ExtendWith(MockitoExtension.class)
class EraSagaStateManagerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(UUID.randomUUID(), UUID.randomUUID());

    @Mock
    EraSagaRepository eraSagaRepository;

    @InjectMocks
    EraSagaStateManager stateManager;

    @Test
    @DisplayName("initRunning — saves new RUNNING saga state with the given gameId, era, and players")
    void initRunning_savesRunningState() {
        // when
        stateManager.initRunning(GAME_ID, 1, PLAYER_IDS);

        // then
        var captor = ArgumentCaptor.forClass(EraSagaState.class);
        then(eraSagaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.gameId()).isEqualTo(GAME_ID);
        assertThat(saved.eraNumber()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo(EraSagaStatus.RUNNING);
        assertThat(saved.playerIds()).isEqualTo(PLAYER_IDS);
    }

    @Test
    @DisplayName("advanceTo — updates saga to the requested status when saga exists")
    void advanceTo_found_updatesStatus() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.RUNNING, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));

        // when
        stateManager.advanceTo(GAME_ID, EraSagaStatus.WAITING_ROUND_1);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.WAITING_ROUND_1));
    }

    @Test
    @DisplayName("advanceTo — does nothing when no saga exists for the gameId")
    void advanceTo_notFound_doesNothing() {
        // given
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        stateManager.advanceTo(GAME_ID, EraSagaStatus.WAITING_ROUND_1);

        // then
        then(eraSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(eraSagaRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("fail — marks saga as FAILED when saga exists")
    void fail_found_marksFailed() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));

        // when
        stateManager.fail(GAME_ID);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.FAILED));
    }

    @Test
    @DisplayName("fail — does nothing when no saga exists for the gameId")
    void fail_notFound_doesNothing() {
        // given
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        stateManager.fail(GAME_ID);

        // then
        then(eraSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(eraSagaRepository).should(org.mockito.BDDMockito.never()).save(any());
    }
}
