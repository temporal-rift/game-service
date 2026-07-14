package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.temporalrift.events.timeline.OutcomeApplied;

@ExtendWith(MockitoExtension.class)
class TimelineOutcomeInboxRepositoryAdapterTest {

    @Mock
    ScoringTimelineOutcomeInboxJpaRepository jpaRepository;

    @InjectMocks
    TimelineOutcomeInboxRepositoryAdapter adapter;

    @Test
    void save_insertsOutcome() {
        var outcome = outcomeApplied();

        adapter.save(outcome);

        var captor = ArgumentCaptor.forClass(ScoringTimelineOutcomeInboxJpaEntity.class);
        then(jpaRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getGameId()).isEqualTo(outcome.gameId());
        assertThat(captor.getValue().getEraNumber()).isEqualTo(outcome.eraNumber());
        assertThat(captor.getValue().getEventId()).isEqualTo(outcome.eventId());
        assertThat(captor.getValue().getWinningOutcomeId()).isEqualTo(outcome.winningOutcomeId());
    }

    @Test
    void save_swallowsConstraintViolationWhenAlreadyStoredConcurrently() {
        var outcome = outcomeApplied();
        willThrow(new DataIntegrityViolationException("duplicate"))
                .given(jpaRepository)
                .saveAndFlush(any());

        assertThatCode(() -> adapter.save(outcome)).doesNotThrowAnyException();
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
