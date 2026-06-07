package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@Component
class CurrentEraFutureEventAdapter implements FutureEventDefinitionPort {

    private final FutureEventDefinitionJpaRepository jpaRepository;

    CurrentEraFutureEventAdapter(FutureEventDefinitionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<EventDefinition> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberOrderByDisplayOrder(gameId, eraNumber).stream()
                .map(entity -> new EventDefinition(
                        entity.getEventId(),
                        entity.getOutcomes().stream()
                                .map(FutureEventOutcomeValue::toDomain)
                                .toList()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceForGameEra(UUID gameId, int eraNumber, List<EventDefinition> definitions) {
        jpaRepository.deleteAllByGameIdAndEraNumber(gameId, eraNumber);
        for (int i = 0; i < definitions.size(); i++) {
            var definition = definitions.get(i);
            var entity = new FutureEventDefinitionJpaEntity();
            entity.setId(UUID.randomUUID());
            entity.setGameId(gameId);
            entity.setEraNumber(eraNumber);
            entity.setEventId(definition.eventId());
            entity.setDisplayOrder(i);
            entity.setOutcomes(definition.outcomes().stream()
                    .map(FutureEventOutcomeValue::fromDomain)
                    .toList());
            jpaRepository.save(entity);
        }
    }
}
