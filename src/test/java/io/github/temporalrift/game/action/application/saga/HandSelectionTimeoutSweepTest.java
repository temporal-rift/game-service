package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;

@ExtendWith(MockitoExtension.class)
class HandSelectionTimeoutSweepTest {
    @Mock
    HandSelectionRepository repository;

    @Mock
    HandSelectionTimeoutProcessor timeoutProcessor;

    @Spy
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    HandSelectionTimeoutSweep sweep;

    @Test
    void sweep_resolvesEveryDueSelectionForRestartRecovery() {
        var firstSelectionId = UUID.randomUUID();
        var secondSelectionId = UUID.randomUUID();
        given(repository.findOpenDueIds(clock.instant())).willReturn(List.of(firstSelectionId, secondSelectionId));

        sweep.sweep();

        then(repository).should().findOpenDueIds(clock.instant());
        then(timeoutProcessor).should().resolve(firstSelectionId);
        then(timeoutProcessor).should().resolve(secondSelectionId);
    }
}
