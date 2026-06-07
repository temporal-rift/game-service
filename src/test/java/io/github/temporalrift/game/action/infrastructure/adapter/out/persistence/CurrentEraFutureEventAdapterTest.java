package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.EventDefinition;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.OutcomeDefinition;

@ExtendWith(MockitoExtension.class)
class CurrentEraFutureEventAdapterTest {

    @Mock
    FutureEventDefinitionJpaRepository jpaRepository;

    @InjectMocks
    CurrentEraFutureEventAdapter adapter;

    @Test
    void replaceForGameEra_replacesRowsInOrder() {
        var gameId = UUID.randomUUID();
        var definitions = List.of(
                new EventDefinition(UUID.randomUUID(), List.of(new OutcomeDefinition(UUID.randomUUID(), 10))),
                new EventDefinition(UUID.randomUUID(), List.of(new OutcomeDefinition(UUID.randomUUID(), 90))));

        adapter.replaceForGameEra(gameId, 2, definitions);

        then(jpaRepository).should().deleteAllByGameIdAndEraNumber(gameId, 2);
        var captor = ArgumentCaptor.forClass(FutureEventDefinitionJpaEntity.class);
        then(jpaRepository).should(org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FutureEventDefinitionJpaEntity::getDisplayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void findByGameIdAndEraNumber_mapsRowsToDefinitions() {
        var gameId = UUID.randomUUID();
        var entity = new FutureEventDefinitionJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(gameId);
        entity.setEraNumber(1);
        entity.setEventId(UUID.randomUUID());
        entity.setDisplayOrder(0);
        entity.setOutcomes(List.of(new FutureEventOutcomeValue(UUID.randomUUID(), 60)));
        given(jpaRepository.findAllByGameIdAndEraNumberOrderByDisplayOrder(gameId, 1))
                .willReturn(List.of(entity));

        var result = adapter.findByGameIdAndEraNumber(gameId, 1);

        assertThat(result)
                .containsExactly(new EventDefinition(
                        entity.getEventId(),
                        List.of(new OutcomeDefinition(
                                entity.getOutcomes().getFirst().outcomeId(), 60))));
    }
}
