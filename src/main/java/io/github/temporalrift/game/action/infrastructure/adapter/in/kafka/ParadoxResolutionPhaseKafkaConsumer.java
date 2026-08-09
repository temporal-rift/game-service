package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class ParadoxResolutionPhaseKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParadoxResolutionPhaseKafkaConsumer.class);
    private static final String PHASE_STARTED = "timeline.ParadoxResolutionPhaseStarted";
    private static final String ERA_COMPLETED = "timeline.EraResolutionCompleted";
    private static final String CONSUMER = "action.paradox-resolution-phase";

    private final ProcessedEventRepository processedEventRepository;
    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final ObjectMapper objectMapper;

    ParadoxResolutionPhaseKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            ParadoxResolutionPhaseRepository phaseRepository,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.phaseRepository = phaseRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.action.paradox-resolution-phase")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(InboundEnvelope envelope) {
        if (isMalformed(envelope)) {
            log.warn("Malformed envelope on timeline.events for paradox phase tracking — discarding");
            return;
        }
        if (!PHASE_STARTED.equals(envelope.eventType()) && !ERA_COMPLETED.equals(envelope.eventType())) {
            return;
        }
        if (envelope.version() != DomainEventEnvelope.SCHEMA_VERSION_V1) {
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
            case PHASE_STARTED -> openPhase(envelope);
            case ERA_COMPLETED -> closePhase(envelope);
            default -> throw new IllegalStateException("Unreachable event type: " + envelope.eventType());
        }
    }

    private boolean isMalformed(InboundEnvelope envelope) {
        return envelope == null
                || envelope.eventId() == null
                || envelope.aggregateId() == null
                || envelope.gameId() == null
                || envelope.occurredAt() == null
                || envelope.payload() == null;
    }

    private void openPhase(InboundEnvelope envelope) {
        var started = objectMapper.convertValue(envelope.payload(), PhaseStartedPayload.class);
        if (hasMismatchedGameId(envelope, started.gameId())) {
            return;
        }
        if (phaseRepository
                .findByGameIdAndEraNumber(started.gameId(), started.eraNumber())
                .isPresent()) {
            return;
        }
        phaseRepository.save(new ParadoxResolutionPhase(
                envelope.eventId(),
                started.gameId(),
                started.eraNumber(),
                envelope.occurredAt().plusSeconds(started.timerSeconds())));
    }

    private void closePhase(InboundEnvelope envelope) {
        var completed = objectMapper.convertValue(envelope.payload(), EraCompletedPayload.class);
        if (hasMismatchedGameId(envelope, completed.gameId())) {
            return;
        }
        phaseRepository
                .findByGameIdAndEraNumberWithLock(completed.gameId(), completed.eraNumber())
                .ifPresent(phase -> {
                    phase.close();
                    phaseRepository.save(phase);
                });
    }

    private boolean hasMismatchedGameId(InboundEnvelope envelope, UUID payloadGameId) {
        if (Objects.equals(envelope.gameId(), payloadGameId)) {
            return false;
        }
        log.warn(
                "{} event {} has payload gameId {} but envelope gameId {} — discarding",
                envelope.eventType(),
                envelope.eventId(),
                payloadGameId,
                envelope.gameId());
        return true;
    }

    record PhaseStartedPayload(UUID gameId, int eraNumber, int timerSeconds) {}

    record EraCompletedPayload(UUID gameId, int eraNumber) {}
}
