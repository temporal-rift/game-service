package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

@Component
class TimelineScoringKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TimelineScoringKafkaConsumer.class);

    private static final String CONSUMER = "scoring.timeline-events";
    private static final String OUTCOME_APPLIED = "timeline.OutcomeApplied";
    private static final String CHAIN_COMPLETED = "timeline.ChainCompleted";
    private static final String CHAIN_BROKEN = "timeline.ChainBroken";
    private static final String ERA_RESOLUTION_COMPLETED = "timeline.EraResolutionCompleted";
    private static final Set<String> SUPPORTED_EVENT_TYPES =
            Set.of(OUTCOME_APPLIED, CHAIN_COMPLETED, CHAIN_BROKEN, ERA_RESOLUTION_COMPLETED);

    private final ProcessedEventRepository processedEventRepository;
    private final TimelineOutcomeInboxRepository outcomeInboxRepository;
    private final EraScoringContextRepository contextRepository;
    private final EraScoringCompletionChecker completionChecker;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    TimelineScoringKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            TimelineOutcomeInboxRepository outcomeInboxRepository,
            EraScoringContextRepository contextRepository,
            EraScoringCompletionChecker completionChecker,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.outcomeInboxRepository = outcomeInboxRepository;
        this.contextRepository = contextRepository;
        this.completionChecker = completionChecker;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.scoring.timeline-events")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(InboundEnvelope envelope) {
        if (envelope.eventId() == null || envelope.payload() == null) {
            log.warn("Malformed envelope on timeline.events (missing eventId or payload) — discarding");
            return;
        }
        if (envelope.eventType() == null || !SUPPORTED_EVENT_TYPES.contains(envelope.eventType())) {
            return;
        }
        // Check version before claiming: claiming an unsupported version would permanently mark the
        // event processed, so it could never be reprocessed once this consumer learns to handle it.
        if (envelope.version() != 1) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    envelope.eventType(),
                    envelope.version(),
                    envelope.eventId());
            return;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER)) {
            log.debug("Duplicate {} event {} ignored", envelope.eventType(), envelope.eventId());
            return;
        }

        switch (envelope.eventType()) {
            case OUTCOME_APPLIED -> handleOutcomeApplied(envelope);
            case CHAIN_COMPLETED -> handleChainCompleted(envelope);
            case CHAIN_BROKEN -> handleChainBroken(envelope);
            case ERA_RESOLUTION_COMPLETED -> handleEraResolutionCompleted(envelope);
            default -> throw new IllegalStateException("Unreachable event type: " + envelope.eventType());
        }
    }

    private void handleOutcomeApplied(InboundEnvelope envelope) {
        var outcome = objectMapper.convertValue(envelope.payload(), OutcomeApplied.class);
        outcomeInboxRepository.save(outcome);
        contextRepository.resolveRevisionistActions(outcome.gameId(), outcome.eraNumber());
        contextRepository
                .resolveActivistDeclarations(outcome.gameId(), outcome.eraNumber())
                .forEach(applicationEventPublisher::publishEvent);
        completionChecker.tryComplete(outcome.gameId(), outcome.eraNumber());
    }

    private void handleChainCompleted(InboundEnvelope envelope) {
        var event = objectMapper.convertValue(envelope.payload(), ChainCompleted.class);
        contextRepository.recordChainFact(
                event.gameId(), event.playerId(), event.chainId(), ScoreReason.CHAIN_COMPLETED, event.eraNumber());
    }

    private void handleChainBroken(InboundEnvelope envelope) {
        var event = objectMapper.convertValue(envelope.payload(), ChainBroken.class);
        contextRepository.recordChainFact(
                event.gameId(), event.targetPlayerId(), event.chainId(), ScoreReason.CHAIN_BROKEN, event.eraNumber());
    }

    private void handleEraResolutionCompleted(InboundEnvelope envelope) {
        var resolution = objectMapper.convertValue(envelope.payload(), EraResolutionCompleted.class);
        contextRepository.saveEraResolutionCompleted(resolution);
        contextRepository.resolveRevisionistActions(resolution.gameId(), resolution.eraNumber());
        contextRepository
                .resolveActivistDeclarations(resolution.gameId(), resolution.eraNumber())
                .forEach(applicationEventPublisher::publishEvent);
        completionChecker.tryComplete(resolution.gameId(), resolution.eraNumber());
    }
}
