package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.AdjustedBandsPublishedPayload;
import io.github.temporalrift.game.session.application.saga.BandsPublishedApplicationEvent;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class BandsPublishedKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID AFFECTED_EVENT_ID = UUID.randomUUID();

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    BandsPublishedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BandsPublishedKafkaConsumer(processedEventRepository, applicationEventPublisher, JSON_MAPPER);
    }

    @Test
    @DisplayName("supported BandsPublished is claimed and published as a typed local event")
    void handle_supportedEvent_publishesLocalEvent() {
        // given
        var payload = new AdjustedBandsPublishedPayload(GAME_ID, 2, List.of());
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.bands-published"))
                .willReturn(true);

        // when
        consumer.handle(message("AdjustedBandsPublished", 1, payload));

        // then
        then(applicationEventPublisher).should().publishEvent(new BandsPublishedApplicationEvent(GAME_ID, 2));
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
        consumer.handle(
                message("timeline.BandsPublished", 1, new AdjustedBandsPublishedPayload(GAME_ID, 2, List.of())));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("record without an eventId header is ignored before claiming")
    void handle_missingEventId_ignored() {
        // given
        var message = MessageBuilder.withPayload((Object) "{}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", "AdjustedBandsPublished")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", "1")
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
        consumer.handle(message("AdjustedBandsPublished", 2, "{}"));

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("duplicate BandsPublished is ignored before payload mapping")
    void handle_duplicateEvent_ignored() {
        // given
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.bands-published"))
                .willReturn(false);

        // when — a body that would fail to deserialize proves the duplicate short-circuits first
        consumer.handle(message("AdjustedBandsPublished", 1, "not-json"));

        // then
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("payload naming a different game than the header is discarded without publishing")
    void handle_mismatchedPayloadGameId_ignored() {
        // given
        var payload = new AdjustedBandsPublishedPayload(UUID.randomUUID(), 2, List.of());
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.bands-published"))
                .willReturn(true);

        // when
        consumer.handle(message("AdjustedBandsPublished", 1, payload));

        // then
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    @DisplayName("malformed BandsPublished payload rolls back before local publication")
    void handle_invalidPayload_throwsBeforePublishing(String description, AdjustedBandsPublishedPayload payload) {
        // given
        var message = message("AdjustedBandsPublished", 1, payload);
        given(processedEventRepository.tryMarkProcessed(EVENT_ID, "session.bands-published"))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> consumer.handle(message)).isInstanceOf(IllegalArgumentException.class);
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    private static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("missing gameId", new AdjustedBandsPublishedPayload(null, 1, List.of())),
                Arguments.of("non-positive eraNumber", new AdjustedBandsPublishedPayload(GAME_ID, 0, List.of())));
    }

    private static Message<Object> message(String eventType, int version, Object payload) {
        var body = payload instanceof String text ? text : JSON_MAPPER.writeValueAsString(payload);
        return MessageBuilder.withPayload((Object) body.getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", eventType)
                .setHeader("eventId", EVENT_ID.toString())
                .setHeader("aggregateId", AFFECTED_EVENT_ID.toString())
                .setHeader("aggregateType", "FutureEvent")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", Instant.EPOCH.toString())
                .setHeader("version", String.valueOf(version))
                .build();
    }
}
