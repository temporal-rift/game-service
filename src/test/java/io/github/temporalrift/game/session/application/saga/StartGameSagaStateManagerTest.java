package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
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

import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@ExtendWith(MockitoExtension.class)
class StartGameSagaStateManagerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID SAGA_ID = UUID.randomUUID();
    static final StartGameSagaState RUNNING_STATE =
            new StartGameSagaState(SAGA_ID, GAME_ID, LOBBY_ID, StartGameSagaStatus.RUNNING, 0, List.of());

    @Mock
    StartGameSagaRepository startGameSagaRepository;

    @InjectMocks
    StartGameSagaStateManager stateManager;

    @Test
    @DisplayName("initRunning — saves new saga state with RUNNING status")
    void initRunning_savesNewRunningState() {
        // when
        stateManager.initRunning(GAME_ID, LOBBY_ID);

        // then
        var captor = ArgumentCaptor.forClass(StartGameSagaState.class);
        then(startGameSagaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.gameId()).isEqualTo(GAME_ID);
        assertThat(saved.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(saved.status()).isEqualTo(StartGameSagaStatus.RUNNING);
        assertThat(saved.sagaId()).isNotNull();
    }

    @Test
    @DisplayName("complete — updates existing saga state to COMPLETED")
    void complete_updatesExistingStateToCompleted() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));

        // when
        stateManager.complete(GAME_ID, LOBBY_ID);

        // then
        then(startGameSagaRepository)
                .should()
                .save(argThat(s -> s.sagaId().equals(SAGA_ID) && s.status() == StartGameSagaStatus.COMPLETED));
    }

    @Test
    @DisplayName("complete — no saga found for gameId, takes no action")
    void complete_sagaNotFound_takesNoAction() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        stateManager.complete(GAME_ID, LOBBY_ID);

        // then
        then(startGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(startGameSagaRepository).shouldHaveNoMoreInteractions();
    }
}
