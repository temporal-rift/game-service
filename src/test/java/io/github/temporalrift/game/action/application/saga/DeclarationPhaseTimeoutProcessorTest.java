package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;

@ExtendWith(MockitoExtension.class)
class DeclarationPhaseTimeoutProcessorTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Mock
    DeclarationPhaseRepository repository;

    @Mock
    ApplicationEventPublisher events;

    @Spy
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks
    DeclarationPhaseTimeoutProcessor processor;

    @Test
    void resolve_closesDuePhaseAndPublishesHandoffOnce() {
        var gameId = UUID.randomUUID();
        var phaseId = UUID.randomUUID();
        var phase = DeclarationPhase.reconstitute(phaseId, gameId, 1, NOW.minusSeconds(1), DeclarationPhaseStatus.OPEN);
        given(repository.findByIdWithLock(phaseId)).willReturn(Optional.of(phase));

        processor.resolve(phaseId);
        processor.resolve(phaseId);

        then(repository).should().save(phase);
        then(events).should().publishEvent(new DeclarationPhaseClosed(gameId, 1));
    }

    @Test
    void resolve_ignoresPhaseBeforeExpiry() {
        var phaseId = UUID.randomUUID();
        var phase = DeclarationPhase.reconstitute(
                phaseId, UUID.randomUUID(), 1, NOW.plusSeconds(30), DeclarationPhaseStatus.OPEN);
        given(repository.findByIdWithLock(phaseId)).willReturn(Optional.of(phase));

        processor.resolve(phaseId);

        then(repository).shouldHaveNoMoreInteractions();
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void resolve_ignoresMissingPhase() {
        var phaseId = UUID.randomUUID();
        given(repository.findByIdWithLock(phaseId)).willReturn(Optional.empty());

        processor.resolve(phaseId);

        then(events).shouldHaveNoInteractions();
    }
}
