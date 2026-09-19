package io.github.temporalrift.game.session.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;

@ExtendWith(MockitoExtension.class)
class DeclarationPhaseClosedListenerTest {

    @Mock
    EraSagaRepository eraSagaRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    DeclarationPhaseClosedListener listener;

    @Test
    void onDeclarationPhaseClosed_startsRoundOneExactlyOnce() {
        var gameId = UUID.randomUUID();
        var players = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(eraSagaRepository.findByGameIdWithLock(gameId))
                .willReturn(Optional.of(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_DECLARATION, players)));

        listener.onDeclarationPhaseClosed(new DeclarationPhaseClosed(gameId, 1));

        var captor = ArgumentCaptor.forClass(EraSagaState.class);
        then(eraSagaRepository).should().save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().status()).isEqualTo(EraSagaStatus.WAITING_ROUND_1);
        then(applicationEventPublisher).should().publishEvent(new StartActionRoundRequested(gameId, 1, 1, players));
    }

    @Test
    void onDeclarationPhaseClosed_ignoresStaleOrAdvancedEra() {
        var gameId = UUID.randomUUID();
        given(eraSagaRepository.findByGameIdWithLock(gameId))
                .willReturn(Optional.of(
                        new EraSagaState(gameId, 1, EraSagaStatus.WAITING_ROUND_1, List.of(UUID.randomUUID()))));

        listener.onDeclarationPhaseClosed(new DeclarationPhaseClosed(gameId, 1));

        then(eraSagaRepository).shouldHaveNoMoreInteractions();
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void onDeclarationPhaseClosed_ignoresUnknownGame() {
        var gameId = UUID.randomUUID();
        given(eraSagaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.empty());

        listener.onDeclarationPhaseClosed(new DeclarationPhaseClosed(gameId, 1));

        then(applicationEventPublisher).shouldHaveNoInteractions();
        then(eraSagaRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void onDeclarationPhaseClosed_ignoresMismatchedEra() {
        var gameId = UUID.randomUUID();
        given(eraSagaRepository.findByGameIdWithLock(gameId))
                .willReturn(Optional.of(
                        new EraSagaState(gameId, 2, EraSagaStatus.WAITING_DECLARATION, List.of(UUID.randomUUID()))));

        listener.onDeclarationPhaseClosed(new DeclarationPhaseClosed(gameId, 1));

        then(eraSagaRepository).shouldHaveNoMoreInteractions();
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }
}
