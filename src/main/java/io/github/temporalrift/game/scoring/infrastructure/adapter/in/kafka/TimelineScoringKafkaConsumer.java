package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.timeline.ChainBroken;
import io.github.temporalrift.events.timeline.ChainCompleted;
import io.github.temporalrift.events.timeline.OutcomeApplied;
import io.github.temporalrift.game.scoring.application.command.UpdateEraScoresCommand;
import io.github.temporalrift.game.scoring.application.command.UpdateScoresCommandHandler;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class TimelineScoringKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TimelineScoringKafkaConsumer.class);

    private static final String CONSUMER = "scoring.timeline-events";
    private static final String OUTCOME_APPLIED = "timeline.OutcomeApplied";
    private static final String CHAIN_COMPLETED = "timeline.ChainCompleted";
    private static final String CHAIN_BROKEN = "timeline.ChainBroken";
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(OUTCOME_APPLIED, CHAIN_COMPLETED, CHAIN_BROKEN);

    private final ProcessedEventRepository processedEventRepository;
    private final TimelineOutcomeInboxRepository outcomeInboxRepository;
    private final ScoringEraCompletionRepository scoringEraCompletionRepository;
    private final EraScoringContextRepository contextRepository;
    private final UpdateScoresCommandHandler updateScoresCommandHandler;
    private final ObjectMapper objectMapper;

    TimelineScoringKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            TimelineOutcomeInboxRepository outcomeInboxRepository,
            ScoringEraCompletionRepository scoringEraCompletionRepository,
            EraScoringContextRepository contextRepository,
            UpdateScoresCommandHandler updateScoresCommandHandler,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.outcomeInboxRepository = outcomeInboxRepository;
        this.scoringEraCompletionRepository = scoringEraCompletionRepository;
        this.contextRepository = contextRepository;
        this.updateScoresCommandHandler = updateScoresCommandHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(EventEnvelope envelope) {
        if (!SUPPORTED_EVENT_TYPES.contains(envelope.eventType())) {
            return;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER)) {
            log.debug("Duplicate {} event {} ignored", envelope.eventType(), envelope.eventId());
            return;
        }
        if (envelope.version() != 1) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    envelope.eventType(),
                    envelope.version(),
                    envelope.eventId());
            return;
        }

        switch (envelope.eventType()) {
            case OUTCOME_APPLIED -> handleOutcomeApplied(envelope);
            case CHAIN_COMPLETED -> handleChainCompleted(envelope);
            case CHAIN_BROKEN -> handleChainBroken(envelope);
            default -> throw new IllegalStateException("Unreachable event type: " + envelope.eventType());
        }
    }

    private void handleOutcomeApplied(EventEnvelope envelope) {
        var outcome = objectMapper.convertValue(envelope.payload(), OutcomeApplied.class);
        outcomeInboxRepository.save(outcome);

        var expectedCount = contextRepository.expectedOutcomeCount(outcome.gameId(), outcome.eraNumber());
        var outcomes = outcomeInboxRepository.findByGameIdAndEraNumber(outcome.gameId(), outcome.eraNumber());
        if (outcomes.size() < expectedCount) {
            return;
        }
        if (!scoringEraCompletionRepository.tryMarkScoringComplete(outcome.gameId(), outcome.eraNumber())) {
            log.debug("Era {} for game {} already scored — skipping", outcome.eraNumber(), outcome.gameId());
            return;
        }
        updateScoresCommandHandler.handle(new UpdateEraScoresCommand(outcome.gameId(), outcome.eraNumber(), outcomes));
    }

    private void handleChainCompleted(EventEnvelope envelope) {
        var event = objectMapper.convertValue(envelope.payload(), ChainCompleted.class);
        contextRepository.recordChainFact(
                event.gameId(), event.playerId(), event.chainId(), ScoreReason.CHAIN_COMPLETED, event.eraNumber());
    }

    private void handleChainBroken(EventEnvelope envelope) {
        var event = objectMapper.convertValue(envelope.payload(), ChainBroken.class);
        contextRepository.recordChainFact(
                event.gameId(), event.targetPlayerId(), event.chainId(), ScoreReason.CHAIN_BROKEN, event.eraNumber());
    }
}
