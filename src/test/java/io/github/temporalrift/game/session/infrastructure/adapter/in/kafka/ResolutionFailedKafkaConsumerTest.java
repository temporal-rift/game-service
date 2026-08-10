package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ResolutionFailedPayload;
import io.github.temporalrift.game.session.application.saga.ResolutionFailedApplicationEvent;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ResolutionFailedKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID AFFECTED_EVENT_ID = UUID.randomUUID();

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    ResolutionFailedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ResolutionFailedKafkaConsumer(processedEventRepository, applicationEventPublisher, JSON_MAPPER);
    }

    @Test
    @DisplayName("supported ResolutionFailed is claimed and published as a typed local event")
    void handle_supportedEvent_publishesLocalEvent() {
        // given
        var payload = new ResolutionFailedPayload(GAME_ID, 2, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID");
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(true);

        // when
        consumer.handle(message("ResolutionFailed", 1, payload));

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
        consumer.handle(message("OutcomeApplied", 1, "{}"));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("the retired namespaced event type is ignored before claiming")
    void handle_namespacedEventType_ignored() {
        // when
        consumer.handle(message(
                "timeline.ResolutionFailed",
                1,
                new ResolutionFailedPayload(GAME_ID, 2, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID")));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("record without an eventId header is ignored before claiming")
    void handle_missingEventId_ignored() {
        // given
        var message = MessageBuilder.withPayload((Object) "{}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", "ResolutionFailed")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", 1)
                .build();

        // when
        consumer.handle(message);

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("unsupported version is skipped before claiming")
    void handle_unsupportedVersion_ignored() {
        // when
        consumer.handle(message("ResolutionFailed", 2, "{}"));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("duplicate ResolutionFailed is ignored before payload mapping")
    void handle_duplicateEvent_ignored() {
        // given
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(false);

        // when — a body that would fail to deserialize proves the duplicate short-circuits first
        consumer.handle(message("ResolutionFailed", 1, "not-json"));

        // then
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    @DisplayName("malformed ResolutionFailed payload rolls back before local publication")
    void handle_invalidPayload_throwsBeforePublishing(String description, ResolutionFailedPayload payload) {
        // given
        var message = message("ResolutionFailed", 1, payload);
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.resolution-failed"))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> consumer.handle(message)).isInstanceOf(IllegalArgumentException.class);
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    private static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of(
                        "missing gameId",
                        new ResolutionFailedPayload(null, 1, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID")),
                Arguments.of(
                        "non-positive eraNumber",
                        new ResolutionFailedPayload(GAME_ID, 0, AFFECTED_EVENT_ID, "PROBABILITY_SUM_INVALID")));
    }

    private static Message<Object> message(String eventType, int version, Object payload) {
        var body = payload instanceof String text ? text : JSON_MAPPER.writeValueAsString(payload);
        return MessageBuilder.withPayload((Object) body.getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", eventType)
                .setHeader("eventId", EVENT_ID.toString())
                .setHeader("aggregateId", AFFECTED_EVENT_ID.toString())
                .setHeader("aggregateType", "FutureEvent")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", Instant.EPOCH)
                .setHeader("version", version)
                .build();
    }
}
