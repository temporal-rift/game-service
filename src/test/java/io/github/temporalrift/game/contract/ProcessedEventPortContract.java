package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;

public interface ProcessedEventPortContract extends PortContract {

    @Test
    default void processedEvent_firstClaimWinsAndDuplicatesAreIgnoredPerConsumer() {
        var repository = port(ProcessedEventRepository.class);
        var eventId = UUID.randomUUID();

        assertThat(repository.tryMarkProcessed(eventId, "consumer-a")).isTrue();
        assertThat(repository.tryMarkProcessed(eventId, "consumer-a")).isFalse();
        assertThat(repository.tryMarkProcessed(eventId, "consumer-b")).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    default void processedEvent_withoutATransactionFailsFast() {
        var repository = port(ProcessedEventRepository.class);

        assertThatThrownBy(() -> repository.tryMarkProcessed(UUID.randomUUID(), "consumer-a"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    default void processedEvent_rollbackReleasesTheClaim() {
        var repository = port(ProcessedEventRepository.class);
        var eventId = UUID.randomUUID();

        assertThatThrownBy(() -> inNewTransaction(() -> {
                    repository.tryMarkProcessed(eventId, "consumer-a");
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(inNewTransaction(() -> repository.tryMarkProcessed(eventId, "consumer-a")))
                .isTrue();
    }
}
