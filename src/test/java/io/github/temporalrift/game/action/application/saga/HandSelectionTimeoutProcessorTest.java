package io.github.temporalrift.game.action.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.HandSelected;

@ExtendWith(MockitoExtension.class)
class HandSelectionTimeoutProcessorTest {
    @Mock
    HandSelectionRepository repository;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    RandomGenerator random;

    @Spy
    Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @InjectMocks
    HandSelectionTimeoutProcessor processor;

    @Test
    void resolve_expiredOpenSelection_persistsAndPublishesOneTimeoutResult() {
        var selection = selection();
        given(repository.findByIdWithLock(selection.id())).willReturn(Optional.of(selection));
        given(random.nextInt(7)).willReturn(0);
        given(random.nextInt(6)).willReturn(0);

        processor.resolve(selection.id());

        then(repository).should().save(any());
        then(events).should().publishEvent(any(HandSelected.class));
    }

    @Test
    void resolve_alreadySelectedSelection_doesNotPublishAgain() {
        var resolved = selection().selectRandomOnExpiry(Instant.EPOCH, new java.util.Random(1));
        given(repository.findByIdWithLock(resolved.id())).willReturn(Optional.of(resolved));

        processor.resolve(resolved.id());

        then(repository).should().findByIdWithLock(resolved.id());
        then(repository).shouldHaveNoMoreInteractions();
        then(events).shouldHaveNoInteractions();
    }

    private static HandSelection selection() {
        var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(slot -> new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.I, slot))
                .toList();
        return HandSelection.open(new HandDealt(UUID.randomUUID(), 1, UUID.randomUUID(), Instant.EPOCH, cards));
    }
}
