package io.github.temporalrift.game.session.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.HandSelected;
import io.github.temporalrift.game.shared.StartActionRoundRequested;

@ExtendWith(MockitoExtension.class)
class HandSelectionEventListenerTest {
    @Mock
    EraSagaRepository eraSagaRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    SessionEventPublisher eventPublisher;

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    HandSelectionEventListener listener;

    @Test
    void onHandSelected_finalPlayer_startsRoundOneExactlyOnce() {
        var gameId = UUID.randomUUID();
        var playerOne = UUID.randomUUID();
        var playerTwo = UUID.randomUUID();
        var state = new EraSagaState(
                gameId, 1, EraSagaStatus.WAITING_HAND_SELECTION, List.of(playerOne, playerTwo), List.of(playerOne));
        given(eraSagaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.of(state));

        listener.onHandSelected(new HandSelected(gameId, 1, playerTwo, HandSelected.SelectionOrigin.PLAYER, List.of()));

        var stateCaptor = ArgumentCaptor.forClass(EraSagaState.class);
        then(eraSagaRepository).should().save(stateCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(stateCaptor.getValue().status())
                .isEqualTo(EraSagaStatus.WAITING_ROUND_1);
        then(applicationEventPublisher)
                .should()
                .publishEvent(new StartActionRoundRequested(gameId, 1, 1, List.of(playerOne, playerTwo)));
        then(eventPublisher).should().publish(any(DomainEventEnvelope.class));
    }

    @Test
    void onHandSelected_nonFinalPlayer_keepsRoundOneClosed() {
        var gameId = UUID.randomUUID();
        var playerOne = UUID.randomUUID();
        var playerTwo = UUID.randomUUID();
        var state = new EraSagaState(gameId, 1, EraSagaStatus.WAITING_HAND_SELECTION, List.of(playerOne, playerTwo));
        given(eraSagaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.of(state));

        listener.onHandSelected(
                new HandSelected(gameId, 1, playerOne, HandSelected.SelectionOrigin.TIMEOUT_RANDOM, List.of()));

        then(applicationEventPublisher).shouldHaveNoInteractions();
        then(eraSagaRepository)
                .should()
                .save(new EraSagaState(
                        gameId,
                        1,
                        EraSagaStatus.WAITING_HAND_SELECTION,
                        List.of(playerOne, playerTwo),
                        List.of(playerOne)));
        then(eventPublisher).should().publish(any(DomainEventEnvelope.class));
    }

    @Test
    void onHandSelected_staleOrDuplicateSelection_doesNotPublish() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var accepted =
                new EraSagaState(gameId, 1, EraSagaStatus.WAITING_HAND_SELECTION, List.of(playerId), List.of(playerId));
        given(eraSagaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.of(accepted));

        listener.onHandSelected(new HandSelected(gameId, 1, playerId, HandSelected.SelectionOrigin.PLAYER, List.of()));

        then(eraSagaRepository).should().findByGameIdWithLock(gameId);
        then(eraSagaRepository).shouldHaveNoMoreInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }
}
