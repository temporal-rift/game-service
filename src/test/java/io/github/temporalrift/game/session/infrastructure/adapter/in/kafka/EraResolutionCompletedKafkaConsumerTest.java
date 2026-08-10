package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

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
    GameRepository gameRepository;

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionActivistDeclarationRepository declarationRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    SessionGameRulesPort gameRules;

    EraResolutionCompletedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EraResolutionCompletedKafkaConsumer(
                processedEventRepository,
                gameRepository,
                lobbyRepository,
                declarationRepository,
                eventPublisher,
                applicationEventPublisher,
                gameRules,
                new TimelineSessionWireMapperImpl(),
                JSON_MAPPER,
                Clock.systemUTC());
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
                        cascadedEventId, io.github.temporalrift.game.shared.CarryOverState.CASCADED));
        then(eventPublisher).should(never()).publish(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
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
                                firstStalledId, io.github.temporalrift.game.shared.CarryOverState.STALLED),
                        new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                                cascadedId, io.github.temporalrift.game.shared.CarryOverState.CASCADED),
                        new io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent(
                                secondStalledId, io.github.temporalrift.game.shared.CarryOverState.STALLED));
        assertThat(game.cascadedParadoxCounter()).isEqualTo(2);
        then(eventPublisher).should(never()).publish(any());
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
                        stalledId, io.github.temporalrift.game.shared.CarryOverState.STALLED));
        assertThat(game.cascadedParadoxCounter()).isEqualTo(initialCascadeCount);
        then(eventPublisher).should(never()).publish(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("reveal-ordered cascades choose the entry that crosses the global threshold")
    void handle_thresholdCrossed_usesRevealOrderedCollapsingEventForActivistWinners() {
        var firstCascadedEvent = UUID.randomUUID();
        var collapsingEvent = UUID.randomUUID();
        // The contract requires EventsDrawn order, but this consumer still sorts by revealIndex so
        // a malformed/reordered transport list cannot award collapse winners for the wrong event.
        var resolution = resolution(2, cascaded(collapsingEvent, 1), cascaded(firstCascadedEvent, 0));
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 1, GameStatus.IN_PROGRESS);
        givenClaimedBarrier();
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(declarationRepository.findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent))
                .willReturn(List.of(PLAYER_2));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(startedLobby()));
        var captor = ArgumentCaptor.forClass(Object.class);

        consumer.handle(messageFor(resolution));

        then(declarationRepository).should().findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent);
        then(applicationEventPublisher).should().publishEvent(captor.capture());
        var collapsed = (TimelineCollapsed) captor.getValue();
        assertThat(collapsed.winners())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactly(PLAYER_2);
        assertThat(collapsed.losers())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_3);
        then(eventPublisher).should().publish(argThat(event -> event.payload() instanceof TimelineCollapsed));
    }

    @Test
    @DisplayName("a barrier without cascades does not lock or mutate the game")
    void handle_withoutCascades_noGameMutation() {
        var resolution = resolution(1, applied(UUID.randomUUID(), 0));
        givenClaimedBarrier();

        consumer.handle(messageFor(resolution));

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(gameRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("duplicate barrier event is ignored without changing the cascade count")
    void handle_duplicateEventId_ignored() {
        var message = messageFor(resolution(1, cascaded(UUID.randomUUID(), 0)));
        given(processedEventRepository.tryMarkProcessed(eventIdOf(message), "session.era-resolution-completed"))
                .willReturn(false);

        consumer.handle(message);

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("unsupported barrier version is skipped before claiming the event")
    void handle_unsupportedVersion_skippedWithoutClaim() {
        consumer.handle(message("EraResolutionCompleted", 2, resolution(1, cascaded(UUID.randomUUID(), 0))));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("the retired namespaced event type is skipped before claiming the event")
    void handle_namespacedEventType_skippedWithoutClaim() {
        consumer.handle(message("timeline.EraResolutionCompleted", 1, resolution(1, cascaded(UUID.randomUUID(), 0))));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
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
                .setHeader("occurredAt", Instant.now())
                .setHeader("version", version)
                .build();
    }

    private static Lobby startedLobby() {
        var players = List.of(
                new LobbyPlayer(PLAYER_1, "P1", Faction.ERASERS, Instant.EPOCH, true),
                new LobbyPlayer(PLAYER_2, "P2", Faction.ACTIVISTS, Instant.EPOCH, true),
                new LobbyPlayer(PLAYER_3, "P3", Faction.REVISIONISTS, Instant.EPOCH, true));
        var config = new LobbyConfig("ABCD2345", 3, 5, Clock.systemUTC());
        return Lobby.reconstitute(LOBBY_ID, GAME_ID, PLAYER_1, players, LobbyStatus.STARTED, config);
    }
}
