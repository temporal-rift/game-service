package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.AdjustedBandsPublishedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.game.session.application.saga.TimelineCollapsePublisher;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class EraResolutionCompletedKafkaConsumerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final UUID PLAYER_3 = UUID.randomUUID();
    static final int MAX_CASCADED = 3;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    EraSagaRepository eraSagaRepository;

    @Mock
    GameRepository gameRepository;

    @Mock
    TimelineCollapsePublisher collapsePublisher;

    @Mock
    SessionGameRulesPort gameRules;

    EraResolutionCompletedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EraResolutionCompletedKafkaConsumer(
                processedEventRepository,
                eraSagaRepository,
                gameRepository,
                collapsePublisher,
                gameRules,
                new TimelineSessionWireMapperImpl(),
                JSON_MAPPER);
    }

    @Test
    @DisplayName("cascades below threshold update the game without publishing TimelineCollapsed")
    void handle_belowThreshold_noTimelineCollapsed() {
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 1, GameStatus.IN_PROGRESS);
        var cascadedEventId = UUID.randomUUID();
        var resolution = resolution(1, cascaded(cascadedEventId, 0));
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);

        consumer.handle(messageFor(resolution));

        then(gameRepository).should().save(game);
        assertThat(game.pendingCarryOverEvents())
                .containsExactly(new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                        cascadedEventId, io.github.temporalrift.game.shared.domain.model.CarryOverState.CASCADED));
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("mixed terminal barrier carries cascaded and stalled IDs in reveal order")
    void handle_mixedCarryForward_preservesRevealOrderAndCountsOnlyCascades() {
        var cascadedId = UUID.randomUUID();
        var firstStalledId = UUID.randomUUID();
        var secondStalledId = UUID.randomUUID();
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 1, GameStatus.IN_PROGRESS);
        var resolution =
                resolution(1, stalled(secondStalledId, 2), cascaded(cascadedId, 1), stalled(firstStalledId, 0));
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);

        consumer.handle(messageFor(resolution));

        assertThat(game.pendingCarryOverEvents())
                .containsExactly(
                        new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                                firstStalledId, io.github.temporalrift.game.shared.domain.model.CarryOverState.STALLED),
                        new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                                cascadedId, io.github.temporalrift.game.shared.domain.model.CarryOverState.CASCADED),
                        new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                                secondStalledId,
                                io.github.temporalrift.game.shared.domain.model.CarryOverState.STALLED));
        assertThat(game.cascadedParadoxCounter()).isEqualTo(2);
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("stalled-only barrier carries forward without affecting collapse")
    void handle_stalledOnly_carriesForwardWithoutChangingCascadeCount() {
        var stalledId = UUID.randomUUID();
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 1, GameStatus.IN_PROGRESS);
        var initialCascadeCount = game.cascadedParadoxCounter();
        var resolution = resolution(1, stalled(stalledId, 0));
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));

        consumer.handle(messageFor(resolution));

        assertThat(game.pendingCarryOverEvents())
                .containsExactly(new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                        stalledId, io.github.temporalrift.game.shared.domain.model.CarryOverState.STALLED));
        assertThat(game.cascadedParadoxCounter()).isEqualTo(initialCascadeCount);
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("threshold crossed while the era awaits scoring — collapse defers to the scoring decision")
    void handle_thresholdCrossedWhileAwaitingScoring_defersWithoutPublishingOrEnding() {
        var firstCascadedEvent = UUID.randomUUID();
        var collapsingEvent = UUID.randomUUID();
        // The contract requires EventsDrawn order, but this consumer still sorts by revealIndex so
        // a malformed/reordered transport list cannot misidentify the threshold-crossing event.
        var resolution = resolution(2, cascaded(collapsingEvent, 1), cascaded(firstCascadedEvent, 0));
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 1, GameStatus.IN_PROGRESS);
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID))
                .willReturn(Optional.of(new EraSagaState(
                        GAME_ID, 2, EraSagaStatus.WAITING_SCORES, List.of(PLAYER_1, PLAYER_2, PLAYER_3))));

        consumer.handle(messageFor(resolution));

        assertThat(game.cascadedParadoxCounter()).isEqualTo(MAX_CASCADED);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("threshold crossed after the era left scoring — late collapse ends the game immediately")
    void handle_thresholdCrossedAfterScoring_publishesCollapseImmediately() {
        var firstCascadedEvent = UUID.randomUUID();
        var collapsingEvent = UUID.randomUUID();
        var resolution = resolution(2, cascaded(collapsingEvent, 1), cascaded(firstCascadedEvent, 0));
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 1, GameStatus.IN_PROGRESS);
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID))
                .willReturn(Optional.of(
                        new EraSagaState(GAME_ID, 2, EraSagaStatus.COMPLETED, List.of(PLAYER_1, PLAYER_2, PLAYER_3))));

        consumer.handle(messageFor(resolution));

        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_COLLAPSE);
        then(collapsePublisher).should().publishCollapse(game, 2, collapsingEvent);
    }

    @Test
    @DisplayName("a barrier without cascades does not lock or mutate the game")
    void handle_withoutCascades_noGameMutation() {
        var resolution = resolution(1, applied(UUID.randomUUID(), 0));
        givenClaimedBarrier();

        consumer.handle(messageFor(resolution));

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(gameRepository).should(never()).save(any());
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("duplicate barrier event is ignored without changing the cascade count")
    void handle_duplicateEventId_ignored() {
        var message = messageFor(resolution(1, cascaded(UUID.randomUUID(), 0)));
        given(processedEventRepository.tryMarkProcessed(eventIdOf(message), "session.era-resolution-completed"))
                .willReturn(false);

        consumer.handle(message);

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("unsupported barrier version is skipped before claiming the event")
    void handle_unsupportedVersion_skippedWithoutClaim() {
        consumer.handle(message("EraResolutionCompleted", 2, resolution(1, cascaded(UUID.randomUUID(), 0))));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("barrier naming a different game than the header is discarded without mutating a game")
    void handle_mismatchedPayloadGameId_noGameMutation() {
        var otherGame = UUID.randomUUID();
        var resolution = new EraResolutionCompletedPayload(otherGame, 1, List.of(cascaded(UUID.randomUUID(), 0)));
        givenClaimedBarrier();

        consumer.handle(messageFor(resolution));

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(gameRepository).should(never()).save(any());
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("the retired namespaced event type is skipped before claiming the event")
    void handle_namespacedEventType_skippedWithoutClaim() {
        consumer.handle(message("timeline.EraResolutionCompleted", 1, resolution(1, cascaded(UUID.randomUUID(), 0))));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("the renamed timeline band correction is skipped before claiming the event")
    void handle_adjustedBandsPublished_skippedWithoutClaim() {
        var body = JSON_MAPPER
                .writeValueAsString(new AdjustedBandsPublishedPayload(GAME_ID, 1, List.of()))
                .getBytes(StandardCharsets.UTF_8);
        var message = MessageBuilder.withPayload((Object) body)
                .setHeader(
                        "eventType",
                        io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract
                                .ADJUSTED_BANDS_PUBLISHED_EVENT_TYPE)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", GAME_ID.toString())
                .setHeader("aggregateType", "Game")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", Instant.now().toString())
                .setHeader("version", "1")
                .build();

        consumer.handle(message);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
        then(collapsePublisher).should(never()).publishCollapse(any(), any(Integer.class), any());
    }

    private void givenClaimedBarrier() {
        given(processedEventRepository.tryMarkProcessed(any(), eq("session.era-resolution-completed")))
                .willReturn(true);
    }

    private static EraResolutionCompletedPayload resolution(
            int eraNumber, EraTerminalResolution... terminalResolutions) {
        return new EraResolutionCompletedPayload(GAME_ID, eraNumber, List.of(terminalResolutions));
    }

    private static EraTerminalResolution cascaded(UUID eventId, int revealIndex) {
        return new EraTerminalResolution(eventId, revealIndex, "CASCADED", null);
    }

    private static EraTerminalResolution applied(UUID eventId, int revealIndex) {
        return new EraTerminalResolution(eventId, revealIndex, "OUTCOME_APPLIED", UUID.randomUUID());
    }

    private static EraTerminalResolution stalled(UUID eventId, int revealIndex) {
        return new EraTerminalResolution(eventId, revealIndex, "STALLED", null);
    }

    private static UUID eventIdOf(Message<Object> message) {
        return UUID.fromString((String) message.getHeaders().get("eventId"));
    }

    private static Message<Object> messageFor(EraResolutionCompletedPayload resolution) {
        return message("EraResolutionCompleted", 1, resolution);
    }

    /** The published wire shape: envelope metadata in the headers, only the typed payload in the body. */
    private static Message<Object> message(String eventType, int version, EraResolutionCompletedPayload resolution) {
        var body = JSON_MAPPER.writeValueAsString(resolution).getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.withPayload((Object) body)
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", GAME_ID.toString())
                .setHeader("aggregateType", "Game")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", Instant.now().toString())
                .setHeader("version", String.valueOf(version))
                .build();
    }
}
