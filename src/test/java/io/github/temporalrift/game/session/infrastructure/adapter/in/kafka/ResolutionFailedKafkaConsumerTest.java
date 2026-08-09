package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.application.saga.ResolutionFailedApplicationEvent;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ResolutionFailedKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID AFFECTED_EVENT_ID = UUID.randomUUID();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    ResolutionFailedKafkaConsumer consumer;

    @Test
    @DisplayName("supported ResolutionFailed is claimed and published as a typed local event")
    void handle_supportedEvent_publishesLocalEvent() {
        // given
        var payload = new ResolutionFailedKafkaConsumer.ResolutionFailedPayload(
                GAME_ID, 2, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID");
        var envelope = envelope("timeline.ResolutionFailed", 1, payload);
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(true);
        given(objectMapper.convertValue(payload, ResolutionFailedKafkaConsumer.ResolutionFailedPayload.class))
                .willReturn(payload);

        // when
        consumer.handle(envelope);

        // then
        then(applicationEventPublisher)
                .should()
                .publishEvent(
                        new ResolutionFailedApplicationEvent(GAME_ID, 2, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID"));
    }

    @Test
    @DisplayName("unrelated timeline event is ignored before claiming")
    void handle_unrelatedEvent_ignored() {
        // when
        consumer.handle(envelope("timeline.OutcomeApplied", 1, new Object()));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("malformed envelope is ignored before claiming")
    void handle_missingEventId_ignored() {
        // given
        var envelope = new InboundEnvelope(
                null, "timeline.ResolutionFailed", GAME_ID, "FutureEvent", GAME_ID, Instant.EPOCH, 1, new Object());

        // when
        consumer.handle(envelope);

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("unsupported version is skipped before claiming")
    void handle_unsupportedVersion_ignored() {
        // when
        consumer.handle(envelope("timeline.ResolutionFailed", 2, new Object()));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("duplicate ResolutionFailed is ignored before payload mapping")
    void handle_duplicateEvent_ignored() {
        // given
        var payload = new Object();
        var envelope = envelope("timeline.ResolutionFailed", 1, payload);
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(false);

        // when
        consumer.handle(envelope);

        // then
        then(objectMapper)
                .should(never())
                .convertValue(any(), eq(ResolutionFailedKafkaConsumer.ResolutionFailedPayload.class));
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    @DisplayName("malformed ResolutionFailed payload rolls back before local publication")
    void handle_invalidPayload_throwsBeforePublishing(
            String description, ResolutionFailedKafkaConsumer.ResolutionFailedPayload payload) {
        // given
        var rawPayload = new Object();
        var envelope = envelope("timeline.ResolutionFailed", 1, rawPayload);
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(true);
        given(objectMapper.convertValue(rawPayload, ResolutionFailedKafkaConsumer.ResolutionFailedPayload.class))
                .willReturn(payload);

        // when / then
        assertThatThrownBy(() -> consumer.handle(envelope)).isInstanceOf(IllegalArgumentException.class);
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    private static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of(
                        "missing gameId",
                        new ResolutionFailedKafkaConsumer.ResolutionFailedPayload(
                                null, 1, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID")),
                Arguments.of(
                        "non-positive eraNumber",
                        new ResolutionFailedKafkaConsumer.ResolutionFailedPayload(
                                GAME_ID, 0, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID")));
    }

    private static InboundEnvelope envelope(String eventType, int version, Object payload) {
        return new InboundEnvelope(
                EVENT_ID, eventType, AFFECTED_EVENT_ID, "FutureEvent", GAME_ID, Instant.EPOCH, version, payload);
    }
}
