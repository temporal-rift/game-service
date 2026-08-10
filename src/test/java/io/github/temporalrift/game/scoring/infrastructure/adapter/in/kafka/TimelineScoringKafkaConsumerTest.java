package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainBrokenPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OutcomeAppliedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxCascadedPayload;
import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

/**
 * Exercises the published {@code timeline.events} wire shape: envelope metadata in Kafka headers, the typed payload
 * as the record body (raw JSON bytes, the way the broker delivers it), and plain AsyncAPI message names as the
 * routing discriminator.
 */
@ExtendWith(MockitoExtension.class)
class TimelineScoringKafkaConsumerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA_NUMBER = 1;
    static final String CONSUMER = "scoring.timeline-events";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

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

    TimelineScoringKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TimelineScoringKafkaConsumer(
                processedEventRepository,
                outcomeInboxRepository,
                contextRepository,
                completionChecker,
                applicationEventPublisher,
                new TimelineScoringWireMapperImpl(),
                JSON_MAPPER);
    }

    @Test
    @DisplayName("unrelated event type — ignored without claiming")
    void handle_wrongEventType_ignored() {
        consumer.handle(message("ResolutionWarning", "{}"));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("namespaced event type from the retired envelope shape — ignored without claiming")
    void handle_namespacedEventType_ignored() {
        consumer.handle(message("timeline.ParadoxCascaded", json(paradoxCascaded(List.of()))));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(contextRepository).should(never()).recordParadoxCascadeFact(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("duplicate eventId — claimed as duplicate, no inbox write, no scoring")
    void handle_duplicateEventId_ignored() {
        var message = message("OutcomeApplied", json(outcomeApplied()));
        givenClaim(message, false);

        consumer.handle(message);

        then(outcomeInboxRepository).should(never()).save(any());
        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }

    @Test
    @DisplayName("unsupported envelope version — skipped without claiming so it can be reprocessed later")
    void handle_unsupportedVersion_skippedWithoutClaiming() {
        consumer.handle(message("OutcomeApplied", json(outcomeApplied()), 2));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }

    @Test
    @DisplayName("missing eventId header — discarded without claiming")
    void handle_missingEventIdHeader_discarded() {
        var message = MessageBuilder.withPayload((Object) json(outcomeApplied()).getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", "OutcomeApplied")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", 1)
                .build();

        consumer.handle(message);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("empty record body — discarded without claiming")
    void handle_emptyBody_discarded() {
        // A record published with a null value converts to KafkaNull, never to a null payload.
        var message = MessageBuilder.withPayload((Object) KafkaNull.INSTANCE)
                .setHeader("eventType", "OutcomeApplied")
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", 1)
                .build();

        consumer.handle(message);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(outcomeInboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("OutcomeApplied — stored, then delegates the completion decision to the checker")
    void handle_outcomeApplied_savesAndDelegatesToCompletionChecker() {
        var payload = outcomeApplied();
        var message = message("OutcomeApplied", json(payload));
        givenClaim(message, true);

        consumer.handle(message);

        then(outcomeInboxRepository)
                .should()
                .save(new OutcomeApplied(
                        GAME_ID, ERA_NUMBER, payload.eventId(), payload.winningOutcomeId(), List.of()));
        then(contextRepository).should().resolveRevisionistActions(GAME_ID, ERA_NUMBER);
        then(completionChecker).should().tryComplete(GAME_ID, ERA_NUMBER);
    }

    @Test
    @DisplayName("EraResolutionCompleted — persists the barrier before resolving declarations and checking completion")
    void handle_eraResolutionCompleted_persistsBarrierThenResolvesDeclarations() {
        var cascadedEventId = UUID.randomUUID();
        var payload = new EraResolutionCompletedPayload(
                GAME_ID, ERA_NUMBER, List.of(new EraTerminalResolution(cascadedEventId, 0, "CASCADED", null)));
        var message = message("EraResolutionCompleted", json(payload));
        givenClaim(message, true);
        given(contextRepository.resolveActivistDeclarations(GAME_ID, ERA_NUMBER))
                .willReturn(List.of());

        consumer.handle(message);

        then(contextRepository)
                .should()
                .saveEraResolutionCompleted(new EraResolutionCompleted(
                        GAME_ID,
                        ERA_NUMBER,
                        List.of(new EraResolutionCompleted.TerminalResolution(
                                cascadedEventId, 0, EraResolutionCompleted.TerminalState.CASCADED, null))));
        then(contextRepository).should().resolveRevisionistActions(GAME_ID, ERA_NUMBER);
        then(contextRepository).should().resolveActivistDeclarations(GAME_ID, ERA_NUMBER);
        then(completionChecker).should().tryComplete(GAME_ID, ERA_NUMBER);
    }

    @Test
    @DisplayName("ChainCompleted — records a chain fact for the completing player, stamped with the event's own era")
    void handle_chainCompleted_recordsChainFact() {
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var message =
                message("ChainCompleted", json(new ChainCompletedPayload(GAME_ID, 2, chainId, playerId, List.of())));
        givenClaim(message, true);

        consumer.handle(message);

        then(contextRepository).should().recordChainFact(GAME_ID, playerId, chainId, ScoreReason.CHAIN_COMPLETED, 2);
    }

    @Test
    @DisplayName(
            "ChainBroken — records a chain fact for the chain owner, not the breaker, stamped with the event's own era")
    void handle_chainBroken_recordsChainFactForTargetPlayer() {
        var brokenByPlayerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var message = message(
                "ChainBroken", json(new ChainBrokenPayload(GAME_ID, 3, chainId, brokenByPlayerId, targetPlayerId, 2)));
        givenClaim(message, true);

        consumer.handle(message);

        then(contextRepository).should().recordChainFact(GAME_ID, targetPlayerId, chainId, ScoreReason.CHAIN_BROKEN, 3);
    }

    @Test
    @DisplayName("ParadoxCascaded — records a paradox cascade fact stamped with the event's own era")
    void handle_paradoxCascaded_recordsParadoxCascadeFact() {
        var detonatedByPlayerIds = List.of(UUID.randomUUID());
        var payload = paradoxCascaded(detonatedByPlayerIds);
        var message = message("ParadoxCascaded", json(payload));
        givenClaim(message, true);

        consumer.handle(message);

        then(contextRepository)
                .should()
                .recordParadoxCascadeFact(
                        GAME_ID, 2, payload.paradoxId(), payload.affectedEventId(), detonatedByPlayerIds);
    }

    @Test
    @DisplayName("ParadoxCascaded without the optional detonatedByPlayerIds — recorded as an empty list")
    void handle_paradoxCascadedWithoutDetonators_recordsEmptyList() {
        var paradoxId = UUID.randomUUID();
        var affectedEventId = UUID.randomUUID();
        var body = """
                {"gameId":"%s","eraNumber":2,"paradoxId":"%s","affectedEventId":"%s","carryForwardProbabilityState":[]}
                """.formatted(GAME_ID, paradoxId, affectedEventId);
        var message = message("ParadoxCascaded", body);
        givenClaim(message, true);

        consumer.handle(message);

        then(contextRepository).should().recordParadoxCascadeFact(GAME_ID, 2, paradoxId, affectedEventId, List.of());
    }

    @Test
    @DisplayName("duplicate ParadoxCascaded eventId — claimed as duplicate, no fact recorded")
    void handle_duplicateParadoxCascadedEventId_ignored() {
        var message = message("ParadoxCascaded", json(paradoxCascaded(List.of())));
        givenClaim(message, false);

        consumer.handle(message);

        then(contextRepository).should(never()).recordParadoxCascadeFact(any(), anyInt(), any(), any(), any());
    }

    private void givenClaim(Message<Object> message, boolean claimed) {
        var eventId = UUID.fromString((String) message.getHeaders().get("eventId"));
        given(processedEventRepository.tryMarkProcessed(eventId, CONSUMER)).willReturn(claimed);
    }

    private static OutcomeAppliedPayload outcomeApplied() {
        return new OutcomeAppliedPayload(GAME_ID, ERA_NUMBER, UUID.randomUUID(), UUID.randomUUID(), List.of());
    }

    private static ParadoxCascadedPayload paradoxCascaded(List<UUID> detonatedByPlayerIds) {
        return new ParadoxCascadedPayload(
                GAME_ID, 2, UUID.randomUUID(), UUID.randomUUID(), List.of(), detonatedByPlayerIds);
    }

    private static String json(Object payload) {
        return JSON_MAPPER.writeValueAsString(payload);
    }

    private static Message<Object> message(String eventType, String body) {
        return message(eventType, body, 1);
    }

    private static Message<Object> message(String eventType, String body, int version) {
        return MessageBuilder.withPayload((Object) body.getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", UUID.randomUUID().toString())
                .setHeader("aggregateType", "FutureEvent")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", Instant.parse("2026-08-09T12:00:00Z"))
                .setHeader("version", version)
                .build();
    }
}
