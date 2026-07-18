package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.scoring.application.command.UpdateEraScoresCommand;
import io.github.temporalrift.game.scoring.application.command.UpdateScoresCommandHandler;
import io.github.temporalrift.game.scoring.domain.event.ChainBroken;
import io.github.temporalrift.game.scoring.domain.event.ChainCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class TimelineScoringKafkaConsumerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA_NUMBER = 1;

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    TimelineOutcomeInboxRepository outcomeInboxRepository;

    @Mock
    ScoringEraCompletionRepository scoringEraCompletionRepository;

    @Mock
    EraScoringContextRepository contextRepository;

    @Mock
    UpdateScoresCommandHandler updateScoresCommandHandler;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    TimelineScoringKafkaConsumer consumer;

    @Test
    @DisplayName("unrelated event type — ignored without claiming")
    void handle_wrongEventType_ignored() {
        var envelope = EventEnvelope.create(GAME_ID, "FutureEvent", GAME_ID, 1, "unrelated");

        consumer.handle(envelope);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("duplicate eventId — claimed as duplicate, no inbox write, no scoring")
    void handle_duplicateEventId_ignored() {
        var outcome = outcomeApplied();
        var envelope = envelopeFor(outcome);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(false);

        consumer.handle(envelope);

        then(objectMapper).should(never()).convertValue(any(), eq(OutcomeApplied.class));
        then(outcomeInboxRepository).should(never()).save(any());
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("unsupported envelope version — skipped without claiming so it can be reprocessed later")
    void handle_unsupportedVersion_skippedWithoutClaiming() {
        // eventType is derived from the payload class (OutcomeApplied -> a supported type), not from
        // the "FutureEvent" aggregateType argument below — this must reach the version check, not the
        // unsupported-event-type check.
        var outcome = outcomeApplied();
        var envelope = EventEnvelope.create(GAME_ID, "FutureEvent", GAME_ID, 2, outcome);

        consumer.handle(envelope);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("OutcomeApplied below expected count — stored but scoring not triggered")
    void handle_outcomeApplied_belowExpectedCount_noScoring() {
        var outcome = outcomeApplied();
        var envelope = claimedEnvelope(outcome);
        given(contextRepository.expectedOutcomeCount(GAME_ID, ERA_NUMBER)).willReturn(3);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));

        consumer.handle(envelope);

        then(outcomeInboxRepository).should().save(outcome);
        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("OutcomeApplied reaches expected count — claims era and triggers scoring")
    void handle_outcomeApplied_atExpectedCount_triggersScoring() {
        var outcome = outcomeApplied();
        var envelope = claimedEnvelope(outcome);
        given(contextRepository.expectedOutcomeCount(GAME_ID, ERA_NUMBER)).willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(true);

        consumer.handle(envelope);

        var captor = ArgumentCaptor.forClass(UpdateEraScoresCommand.class);
        then(updateScoresCommandHandler).should().handle(captor.capture());
        var command = captor.getValue();
        assertThatCommand(command);
    }

    @Test
    @DisplayName("era already scored by another claimant — handler not called")
    void handle_outcomeApplied_alreadyScored_handlerNotCalled() {
        var outcome = outcomeApplied();
        var envelope = claimedEnvelope(outcome);
        given(contextRepository.expectedOutcomeCount(GAME_ID, ERA_NUMBER)).willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(false);

        consumer.handle(envelope);

        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("ChainCompleted — records a chain fact for the completing player, stamped with the event's own era")
    void handle_chainCompleted_recordsChainFact() {
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var chainCompleted = new ChainCompleted(GAME_ID, 2, chainId, playerId, List.of());
        var envelope = EventEnvelope.create(GAME_ID, "WeaverChain", GAME_ID, 1, chainCompleted);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(true);
        given(objectMapper.convertValue(envelope.payload(), ChainCompleted.class))
                .willReturn(chainCompleted);

        consumer.handle(envelope);

        then(contextRepository).should().recordChainFact(GAME_ID, playerId, chainId, ScoreReason.CHAIN_COMPLETED, 2);
    }

    @Test
    @DisplayName(
            "ChainBroken — records a chain fact for the chain owner, not the breaker, stamped with the event's own era")
    void handle_chainBroken_recordsChainFactForTargetPlayer() {
        var brokenByPlayerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var chainBroken = new ChainBroken(GAME_ID, 3, chainId, brokenByPlayerId, targetPlayerId, 2);
        var envelope = EventEnvelope.create(GAME_ID, "WeaverChain", GAME_ID, 1, chainBroken);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(true);
        given(objectMapper.convertValue(envelope.payload(), ChainBroken.class)).willReturn(chainBroken);

        consumer.handle(envelope);

        then(contextRepository).should().recordChainFact(GAME_ID, targetPlayerId, chainId, ScoreReason.CHAIN_BROKEN, 3);
    }

    private void assertThatCommand(UpdateEraScoresCommand command) {
        assertThat(command.gameId()).isEqualTo(GAME_ID);
        assertThat(command.eraNumber()).isEqualTo(ERA_NUMBER);
        assertThat(command.outcomes()).hasSize(1);
    }

    private EventEnvelope claimedEnvelope(OutcomeApplied outcome) {
        var envelope = envelopeFor(outcome);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(true);
        given(objectMapper.convertValue(envelope.payload(), OutcomeApplied.class))
                .willReturn(outcome);
        return envelope;
    }

    private static OutcomeApplied outcomeApplied() {
        return new OutcomeApplied(GAME_ID, ERA_NUMBER, UUID.randomUUID(), UUID.randomUUID(), List.of());
    }

    private static EventEnvelope envelopeFor(OutcomeApplied outcome) {
        return EventEnvelope.create(GAME_ID, "FutureEvent", GAME_ID, 1, outcome);
    }
}
