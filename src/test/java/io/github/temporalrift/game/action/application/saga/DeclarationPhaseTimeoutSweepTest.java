package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.shared.domain.event.LogicalClockAdvanced;

@ExtendWith(MockitoExtension.class)
class DeclarationPhaseTimeoutSweepTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    DeclarationPhaseRepository repository;

    @Mock
    DeclarationPhaseTimeoutProcessor processor;

    @Test
    @DisplayName("a sweep resolves every open phase due at the current time, on schedule and on a clock advance")
    void sweep_resolvesDuePhases() {
        var sweep = new DeclarationPhaseTimeoutSweep(repository, Clock.fixed(NOW, ZoneOffset.UTC), processor);
        var phaseId = UUID.randomUUID();
        given(repository.findOpenDueIds(NOW)).willReturn(List.of(phaseId));

        sweep.sweep();
        sweep.onLogicalClockAdvanced(new LogicalClockAdvanced(NOW));

        then(processor).should(times(2)).resolve(phaseId);
    }
}
