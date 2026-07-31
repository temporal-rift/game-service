package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.event.ChainBroken;
import io.github.temporalrift.game.scoring.domain.event.ChainCompleted;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.InboundEnvelope;
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
    EraScoringContextRepository contextRepository;

    @Mock
    EraScoringCompletionChecker completionChecker;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    TimelineScoringKafkaConsumer consumer;

    @Test
    @DisplayName("unrelated event type — ignored without claiming")
    void handle_wrongEventType_ignored() {
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.Unrelated",
                GAME_ID,
                "FutureEvent",
                GAME_ID,
                Instant.now(),
                1,
                "unrelated");

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
        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }

    @Test
    @DisplayName("unsupported envelope version — skipped without claiming so it can be reprocessed later")
    void handle_unsupportedVersion_skippedWithoutClaiming() {
        var outcome = outcomeApplied();
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.OutcomeApplied",
                GAME_ID,
                "FutureEvent",
                GAME_ID,
                Instant.now(),
                2,
                outcome);

        consumer.handle(envelope);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }

    @Test
    @DisplayName("OutcomeApplied — stored, then delegates the completion decision to the checker")
    void handle_outcomeApplied_savesAndDelegatesToCompletionChecker() {
        var outcome = outcomeApplied();
        var envelope = claimedEnvelope(outcome);

        consumer.handle(envelope);

        then(outcomeInboxRepository).should().save(outcome);
        then(completionChecker).should().tryComplete(GAME_ID, ERA_NUMBER);
    }

    @Test
    @DisplayName("EraResolutionCompleted — persists the barrier before resolving declarations and checking completion")
    void handle_eraResolutionCompleted_persistsBarrierThenResolvesDeclarations() {
        var resolution = new EraResolutionCompleted(
                GAME_ID,
                ERA_NUMBER,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        UUID.randomUUID(), 0, EraResolutionCompleted.TerminalState.CASCADED, null)));
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.EraResolutionCompleted",
                GAME_ID,
                "EraResolution",
                GAME_ID,
                Instant.now(),
                1,
                resolution);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(true);
        given(objectMapper.convertValue(envelope.payload(), EraResolutionCompleted.class))
                .willReturn(resolution);
        given(contextRepository.resolveActivistDeclarations(GAME_ID, ERA_NUMBER))
                .willReturn(List.of());

        consumer.handle(envelope);

        then(contextRepository).should().saveEraResolutionCompleted(resolution);
        then(contextRepository).should().resolveActivistDeclarations(GAME_ID, ERA_NUMBER);
        then(completionChecker).should().tryComplete(GAME_ID, ERA_NUMBER);
    }

    @Test
    @DisplayName("ChainCompleted — records a chain fact for the completing player, stamped with the event's own era")
    void handle_chainCompleted_recordsChainFact() {
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var chainCompleted = new ChainCompleted(GAME_ID, 2, chainId, playerId, List.of());
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.ChainCompleted",
                GAME_ID,
                "WeaverChain",
                GAME_ID,
                Instant.now(),
                1,
                chainCompleted);
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
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.ChainBroken",
                GAME_ID,
                "WeaverChain",
                GAME_ID,
                Instant.now(),
                1,
                chainBroken);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "scoring.timeline-events"))
                .willReturn(true);
        given(objectMapper.convertValue(envelope.payload(), ChainBroken.class)).willReturn(chainBroken);

        consumer.handle(envelope);

        then(contextRepository).should().recordChainFact(GAME_ID, targetPlayerId, chainId, ScoreReason.CHAIN_BROKEN, 3);
    }

    private InboundEnvelope claimedEnvelope(OutcomeApplied outcome) {
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

    private static InboundEnvelope envelopeFor(OutcomeApplied outcome) {
        return new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.OutcomeApplied",
                GAME_ID,
                "FutureEvent",
                GAME_ID,
                Instant.now(),
                1,
                outcome);
    }
}
