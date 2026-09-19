package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.shared.domain.event.HandSelectionCompleted;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@ExtendWith(MockitoExtension.class)
class DeclarationPhaseEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Mock
    DeclarationPhaseRepository repository;

    @Mock
    DeclarationPhaseTimerScheduler timerScheduler;

    @Mock
    GameRulesPort gameRules;

    @Mock
    Clock clock;

    @InjectMocks
    DeclarationPhaseEventListener listener;

    @Test
    void onHandSelectionCompleted_opensPhaseAndSchedulesExpiry() {
        var gameId = UUID.randomUUID();
        var players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(clock.instant()).willReturn(NOW);
        given(gameRules.declarationTimerSeconds(3)).willReturn(45);
        given(repository.createIfAbsent(any(DeclarationPhase.class))).willReturn(true);

        listener.onHandSelectionCompleted(new HandSelectionCompleted(gameId, 2, players));

        var captor = ArgumentCaptor.forClass(DeclarationPhase.class);
        then(repository).should().createIfAbsent(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(gameId);
        assertThat(captor.getValue().eraNumber()).isEqualTo(2);
        assertThat(captor.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(45));
        then(timerScheduler).should().scheduleAfterCommit(captor.getValue().id(), NOW.plusSeconds(45));
    }

    @Test
    void onHandSelectionCompleted_duplicateOpenDoesNotReschedule() {
        given(clock.instant()).willReturn(NOW);
        given(gameRules.declarationTimerSeconds(2)).willReturn(30);
        given(repository.createIfAbsent(any(DeclarationPhase.class))).willReturn(false);

        listener.onHandSelectionCompleted(
                new HandSelectionCompleted(UUID.randomUUID(), 1, List.of(UUID.randomUUID(), UUID.randomUUID())));

        then(timerScheduler).should(never()).scheduleAfterCommit(any(), any());
    }
}
