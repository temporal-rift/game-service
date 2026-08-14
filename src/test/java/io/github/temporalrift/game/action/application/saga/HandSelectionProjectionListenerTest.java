package io.github.temporalrift.game.action.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;

@ExtendWith(MockitoExtension.class)
class HandSelectionProjectionListenerTest {
    @Mock
    HandSelectionRepository repository;

    @Mock
    HandSelectionTimerScheduler timerScheduler;

    @InjectMocks
    HandSelectionProjectionListener listener;

    @Test
    void handDealt_createsAndSchedulesAnOpenSelection() {
        var event = event();
        given(repository.findByGameIdAndEraNumberAndPlayerIdWithLock(
                        event.gameId(), event.eraNumber(), event.playerId()))
                .willReturn(Optional.empty());

        listener.onHandDealt(event);

        then(repository).should().save(any());
        then(timerScheduler)
                .should()
                .scheduleAfterCommit(any(), org.mockito.ArgumentMatchers.eq(event.selectionExpiresAt()));
    }

    @Test
    void duplicateHandDealt_doesNotReplaceOrRescheduleAnExistingSelection() {
        var event = event();
        given(repository.findByGameIdAndEraNumberAndPlayerIdWithLock(
                        event.gameId(), event.eraNumber(), event.playerId()))
                .willReturn(
                        Optional.of(io.github.temporalrift.game.action.domain.handselection.HandSelection.open(event)));

        listener.onHandDealt(event);

        then(repository)
                .should()
                .findByGameIdAndEraNumberAndPlayerIdWithLock(event.gameId(), event.eraNumber(), event.playerId());
        then(repository).shouldHaveNoMoreInteractions();
        then(timerScheduler).shouldHaveNoInteractions();
    }

    private static HandDealt event() {
        var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(slot -> new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.I, slot))
                .toList();
        return new HandDealt(UUID.randomUUID(), 1, UUID.randomUUID(), Instant.parse("2026-08-14T00:01:00Z"), cards);
    }
}
