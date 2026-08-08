package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.domain.event.EraResolutionCompleted;
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
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class EraResolutionCompletedKafkaConsumerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final UUID PLAYER_3 = UUID.randomUUID();
    static final int MAX_CASCADED = 3;

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

    @Mock
    ObjectMapper objectMapper;

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    EraResolutionCompletedKafkaConsumer consumer;

    @Test
    @DisplayName("cascades below threshold update the game without publishing TimelineCollapsed")
    void handle_belowThreshold_noTimelineCollapsed() {
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 1, GameStatus.IN_PROGRESS);
        var cascadedEventId = UUID.randomUUID();
        var resolution = resolution(1, cascaded(cascadedEventId, 0));
        givenProcessedResolution(resolution);
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);

        consumer.handle(envelopeFor(resolution));

        then(gameRepository).should().save(game);
        assertThat(game.pendingCascadedEventIds()).containsExactly(cascadedEventId);
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
        givenProcessedResolution(resolution);
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);

        consumer.handle(envelopeFor(resolution));

        assertThat(game.pendingCascadedEventIds()).containsExactly(firstStalledId, cascadedId, secondStalledId);
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
        givenProcessedResolution(resolution);
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));

        consumer.handle(envelopeFor(resolution));

        assertThat(game.pendingCascadedEventIds()).containsExactly(stalledId);
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
        givenProcessedResolution(resolution);
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(declarationRepository.findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent))
                .willReturn(List.of(PLAYER_2));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(startedLobby()));
        var captor = ArgumentCaptor.forClass(Object.class);

        consumer.handle(envelopeFor(resolution));

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
        givenProcessedResolution(resolution);

        consumer.handle(envelopeFor(resolution));

        then(gameRepository).should(never()).findByIdWithLock(any());
        then(gameRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("duplicate barrier event is ignored without changing the cascade count")
    void handle_duplicateEventId_ignored() {
        var resolution = resolution(1, cascaded(UUID.randomUUID(), 0));
        var envelope = envelopeFor(resolution);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.era-resolution-completed"))
                .willReturn(false);

        consumer.handle(envelope);

        then(objectMapper).should(never()).convertValue(any(), eq(EraResolutionCompleted.class));
        then(gameRepository).should(never()).findByIdWithLock(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("unsupported barrier version is skipped before claiming the event")
    void handle_unsupportedVersion_skippedWithoutClaim() {
        var envelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.EraResolutionCompleted",
                GAME_ID,
                "Game",
                GAME_ID,
                Instant.now(),
                2,
                resolution(1, cascaded(UUID.randomUUID(), 0)));

        consumer.handle(envelope);

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findByIdWithLock(any());
    }

    private void givenProcessedResolution(EraResolutionCompleted resolution) {
        given(objectMapper.convertValue(any(), eq(EraResolutionCompleted.class)))
                .willReturn(resolution);
        given(processedEventRepository.tryMarkProcessed(any(), eq("session.era-resolution-completed")))
                .willReturn(true);
    }

    private static EraResolutionCompleted resolution(
            int eraNumber, EraResolutionCompleted.TerminalResolution... terminalResolutions) {
        return new EraResolutionCompleted(GAME_ID, eraNumber, List.of(terminalResolutions));
    }

    private static EraResolutionCompleted.TerminalResolution cascaded(UUID eventId, int revealIndex) {
        return new EraResolutionCompleted.TerminalResolution(
                eventId, revealIndex, EraResolutionCompleted.TerminalState.CASCADED, null);
    }

    private static EraResolutionCompleted.TerminalResolution applied(UUID eventId, int revealIndex) {
        return new EraResolutionCompleted.TerminalResolution(
                eventId, revealIndex, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, UUID.randomUUID());
    }

    private static EraResolutionCompleted.TerminalResolution stalled(UUID eventId, int revealIndex) {
        return new EraResolutionCompleted.TerminalResolution(
                eventId, revealIndex, EraResolutionCompleted.TerminalState.STALLED, null);
    }

    private static InboundEnvelope envelopeFor(EraResolutionCompleted resolution) {
        return new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.EraResolutionCompleted",
                GAME_ID,
                "Game",
                GAME_ID,
                Instant.now(),
                1,
                resolution);
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
