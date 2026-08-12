package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxResolutionPhaseStartedPayload;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ParadoxResolutionPhaseKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PHASE_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T12:00:00Z");

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    ParadoxResolutionPhaseKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParadoxResolutionPhaseKafkaConsumer(processedEventRepository, phaseRepository, JSON_MAPPER);
    }

    @Test
    void phaseStartedCreatesDurablePhaseWithAdvertisedExpiry() {
        var message = message(
                "ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(UUID.randomUUID()), 30));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());

        consumer.handle(message);

        var captor = ArgumentCaptor.forClass(ParadoxResolutionPhase.class);
        then(phaseRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(eventIdOf(message));
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().expiresAt()).isEqualTo(OCCURRED_AT.plusSeconds(30));
    }

    @Test
    void eraCompletionClosesExistingPhase() {
        var phase = new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30));
        var message = message("EraResolutionCompleted", 1, eraCompleted(GAME_ID));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));

        consumer.handle(message);

        assertThat(phase.status()).isEqualTo(ParadoxResolutionPhaseStatus.CLOSED);
        then(phaseRepository).should().save(phase);
    }

    @Test
    void duplicateEventIsIgnoredBeforeDeserialization() {
        // A body that cannot deserialize proves the duplicate claim short-circuits before the payload is read.
        var message = message("ParadoxResolutionPhaseStarted", 1, "not-json");
        givenClaim(message, false);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void phaseStartedWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var message = message(
                "ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(UUID.randomUUID(), ERA, List.of(), 30));
        givenClaim(message, true);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void eraCompletionWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var message = message("EraResolutionCompleted", 1, eraCompleted(UUID.randomUUID()));
        givenClaim(message, true);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unsupportedVersionIsSkippedBeforeClaim() {
        consumer.handle(message(
                "ParadoxResolutionPhaseStarted",
                2,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(), 30)));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void namespacedEventTypeIsSkippedBeforeClaim() {
        consumer.handle(message(
                "timeline.ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(), 30)));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unrelatedAndMalformedEventsAreIgnored() {
        consumer.handle(message("OutcomeApplied", 1, "{}"));
        consumer.handle(MessageBuilder.withPayload((Object) "{}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", "ParadoxResolutionPhaseStarted")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", "1")
                .build());

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    private void givenClaim(Message<Object> message, boolean claimed) {
        given(processedEventRepository.tryMarkProcessed(eventIdOf(message), "action.paradox-resolution-phase"))
                .willReturn(claimed);
    }

    private static EraResolutionCompletedPayload eraCompleted(UUID gameId) {
        return new EraResolutionCompletedPayload(gameId, ERA, List.of());
    }

    private static UUID eventIdOf(Message<Object> message) {
        return UUID.fromString((String) message.getHeaders().get("eventId"));
    }

    /** The published wire shape: envelope metadata in the headers, only the typed payload in the body. */
    private static Message<Object> message(String eventType, int version, Object payload) {
        var body = payload instanceof String text ? text : JSON_MAPPER.writeValueAsString(payload);
        return MessageBuilder.withPayload((Object) body.getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", PHASE_ID.toString())
                .setHeader("aggregateType", "ParadoxResolutionPhase")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", OCCURRED_AT.toString())
                .setHeader("version", String.valueOf(version))
                .build();
    }
}
