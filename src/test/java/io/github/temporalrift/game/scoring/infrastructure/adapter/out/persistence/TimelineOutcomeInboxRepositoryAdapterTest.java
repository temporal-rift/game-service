package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.timeline.OutcomeApplied;

@ExtendWith(MockitoExtension.class)
class TimelineOutcomeInboxRepositoryAdapterTest {

    @Mock
    ScoringTimelineOutcomeInboxJpaRepository jpaRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    TimelineOutcomeInboxRepositoryAdapter adapter;

    @Test
    void save_insertsOutcomeViaConflictFreeNativeQuery() {
        var outcome = outcomeApplied();
        given(objectMapper.writeValueAsString(outcome)).willReturn("{\"json\":true}");

        adapter.save(outcome);

        then(jpaRepository)
                .should()
                .insertIfAbsent(
                        any(),
                        eq(outcome.gameId()),
                        eq(outcome.eraNumber()),
                        eq(outcome.eventId()),
                        eq(outcome.winningOutcomeId()),
                        eq("{\"json\":true}"));
    }

    @Test
    void findByGameIdAndEraNumber_mapsStoredEntitiesToDomain() {
        var outcome = outcomeApplied();
        var entity = ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome);
        given(jpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(outcome.gameId(), outcome.eraNumber()))
                .willReturn(List.of(entity));

        var outcomes = adapter.findByGameIdAndEraNumber(outcome.gameId(), outcome.eraNumber());

        assertThat(outcomes).containsExactly(outcome);
    }

    private static OutcomeApplied outcomeApplied() {
        return new OutcomeApplied(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of());
    }
}
