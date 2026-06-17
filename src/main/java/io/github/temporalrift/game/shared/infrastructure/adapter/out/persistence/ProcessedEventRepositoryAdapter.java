package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository jpaRepository;

    ProcessedEventRepositoryAdapter(ProcessedEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean tryMarkProcessed(UUID eventId, String consumer) {
        return jpaRepository.insertIfAbsent(Objects.requireNonNull(eventId), Objects.requireNonNull(consumer)) == 1;
    }
}
