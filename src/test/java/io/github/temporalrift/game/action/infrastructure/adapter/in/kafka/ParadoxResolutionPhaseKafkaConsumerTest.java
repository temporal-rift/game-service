package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ParadoxResolutionPhaseKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PHASE_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T12:00:00Z");

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    @Mock
    ObjectMapper objectMapper;

    ParadoxResolutionPhaseKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParadoxResolutionPhaseKafkaConsumer(processedEventRepository, phaseRepository, objectMapper);
    }

    @Test
    void phaseStartedCreatesDurablePhaseWithAdvertisedExpiry() {
        var envelope = envelope("timeline.ParadoxResolutionPhaseStarted", new Object());
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "action.paradox-resolution-phase"))
                .willReturn(true);
        given(objectMapper.convertValue(any(), eq(ParadoxResolutionPhaseKafkaConsumer.PhaseStartedPayload.class)))
                .willReturn(new ParadoxResolutionPhaseKafkaConsumer.PhaseStartedPayload(GAME_ID, ERA, 30));
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());

        consumer.handle(envelope);

        var captor = ArgumentCaptor.forClass(ParadoxResolutionPhase.class);
        then(phaseRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(envelope.eventId());
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().expiresAt()).isEqualTo(OCCURRED_AT.plusSeconds(30));
    }

    @Test
    void eraCompletionClosesExistingPhase() {
        var phase = new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30));
        var envelope = envelope("timeline.EraResolutionCompleted", new Object());
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "action.paradox-resolution-phase"))
                .willReturn(true);
        given(objectMapper.convertValue(any(), eq(ParadoxResolutionPhaseKafkaConsumer.EraCompletedPayload.class)))
                .willReturn(new ParadoxResolutionPhaseKafkaConsumer.EraCompletedPayload(GAME_ID, ERA));
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));

        consumer.handle(envelope);

        assertThat(phase.status()).isEqualTo(ParadoxResolutionPhaseStatus.CLOSED);
        then(phaseRepository).should().save(phase);
    }

    @Test
    void duplicateEventIsIgnoredBeforeDeserialization() {
        var envelope = envelope("timeline.ParadoxResolutionPhaseStarted", new Object());
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "action.paradox-resolution-phase"))
                .willReturn(false);

        consumer.handle(envelope);

        then(objectMapper).shouldHaveNoInteractions();
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void phaseStartedWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var envelope = envelope("timeline.ParadoxResolutionPhaseStarted", new Object());
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "action.paradox-resolution-phase"))
                .willReturn(true);
        given(objectMapper.convertValue(any(), eq(ParadoxResolutionPhaseKafkaConsumer.PhaseStartedPayload.class)))
                .willReturn(new ParadoxResolutionPhaseKafkaConsumer.PhaseStartedPayload(UUID.randomUUID(), ERA, 30));

        consumer.handle(envelope);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void eraCompletionWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var envelope = envelope("timeline.EraResolutionCompleted", new Object());
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "action.paradox-resolution-phase"))
                .willReturn(true);
        given(objectMapper.convertValue(any(), eq(ParadoxResolutionPhaseKafkaConsumer.EraCompletedPayload.class)))
                .willReturn(new ParadoxResolutionPhaseKafkaConsumer.EraCompletedPayload(UUID.randomUUID(), ERA));

        consumer.handle(envelope);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unsupportedVersionIsSkippedBeforeClaim() {
        var valid = envelope("timeline.ParadoxResolutionPhaseStarted", new Object());
        var envelope = new InboundEnvelope(
                valid.eventId(),
                valid.eventType(),
                valid.aggregateId(),
                valid.aggregateType(),
                valid.gameId(),
                valid.occurredAt(),
                2,
                valid.payload());

        consumer.handle(envelope);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unrelatedAndMalformedEventsAreIgnored() {
        consumer.handle(envelope("timeline.OutcomeApplied", new Object()));
        consumer.handle(envelope("timeline.ParadoxResolutionPhaseStarted", null));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    private InboundEnvelope envelope(String eventType, Object payload) {
        return new InboundEnvelope(
                UUID.randomUUID(), eventType, PHASE_ID, "ParadoxResolutionPhase", GAME_ID, OCCURRED_AT, 1, payload);
    }
}
