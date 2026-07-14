package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.timeline.OutcomeApplied;

@ExtendWith(MockitoExtension.class)
class TimelineOutcomeInboxRepositoryAdapterTest {

    @Mock
    ScoringTimelineOutcomeInboxJpaRepository jpaRepository;

    @InjectMocks
    TimelineOutcomeInboxRepositoryAdapter adapter;

    @Test
    void save_insertsWhenNotAlreadyStored() {
        var outcome = outcomeApplied();
        given(jpaRepository.findByGameIdAndEraNumberAndEventId(
                        outcome.gameId(), outcome.eraNumber(), outcome.eventId()))
                .willReturn(Optional.empty());

        adapter.save(outcome);

        var captor = ArgumentCaptor.forClass(ScoringTimelineOutcomeInboxJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        assertThat(captor.getValue().getGameId()).isEqualTo(outcome.gameId());
        assertThat(captor.getValue().getEraNumber()).isEqualTo(outcome.eraNumber());
        assertThat(captor.getValue().getEventId()).isEqualTo(outcome.eventId());
        assertThat(captor.getValue().getWinningOutcomeId()).isEqualTo(outcome.winningOutcomeId());
    }

    @Test
    void save_skipsWhenAlreadyStored() {
        var outcome = outcomeApplied();
        var existing = ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome);
        given(jpaRepository.findByGameIdAndEraNumberAndEventId(
                        outcome.gameId(), outcome.eraNumber(), outcome.eventId()))
                .willReturn(Optional.of(existing));

        adapter.save(outcome);

        then(jpaRepository).should(never()).save(any());
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
