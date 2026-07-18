package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.ParadoxCascaded;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ParadoxCascadedKafkaConsumerTest {

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
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    SessionGameRulesPort gameRules;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    ParadoxCascadedKafkaConsumer consumer;

    // ─── threshold not reached ────────────────────────────────────────────────

    @Test
    @DisplayName("paradox below threshold — game saved but no TimelineCollapsed published")
    void handle_belowThreshold_noTimelineCollapsed() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 1, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        var paradox = paradoxCascaded(1);
        given(objectMapper.convertValue(any(), eq(ParadoxCascaded.class))).willReturn(paradox);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.paradox-cascaded"))
                .willReturn(true);

        // when
        consumer.handle(envelope);

        // then
        then(gameRepository).should().save(game);
        then(eventPublisher).should(never()).publish(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    // ─── threshold reached ────────────────────────────────────────────────────

    @Test
    @DisplayName("third paradox — TimelineCollapsed published to outbox and as Spring event")
    void handle_atThreshold_publishesTimelineCollapsedBothWays() {
        // given — 2 existing, this is the 3rd = threshold
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 2, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(lobbyRepository.findById(LOBBY_ID))
                .willReturn(Optional.of(startedLobby(List.of(
                        new LobbyPlayer(PLAYER_1, "P1", Faction.PROPHETS, java.time.Instant.EPOCH, true),
                        new LobbyPlayer(PLAYER_2, "P2", Faction.WEAVERS, java.time.Instant.EPOCH, true)))));
        var paradox = paradoxCascaded(2);
        given(objectMapper.convertValue(any(), eq(ParadoxCascaded.class))).willReturn(paradox);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.paradox-cascaded"))
                .willReturn(true);

        // when
        consumer.handle(envelope);

        // then
        then(eventPublisher).should().publish(argThat(e -> e.payload() instanceof TimelineCollapsed));
        then(applicationEventPublisher).should().publishEvent(any(TimelineCollapsed.class));
    }

    @Test
    @DisplayName("TimelineCollapsed — Erasers and Revisionists are winners, others are losers")
    void handle_atThreshold_erasersAndRevisionistsWin() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 2, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.maxCascadedParadoxes()).willReturn(MAX_CASCADED);
        given(lobbyRepository.findById(LOBBY_ID))
                .willReturn(Optional.of(startedLobby(List.of(
                        new LobbyPlayer(PLAYER_1, "P1", Faction.ERASERS, java.time.Instant.EPOCH, true),
                        new LobbyPlayer(PLAYER_2, "P2", Faction.REVISIONISTS, java.time.Instant.EPOCH, true),
                        new LobbyPlayer(PLAYER_3, "P3", Faction.PROPHETS, java.time.Instant.EPOCH, true)))));
        var paradox = paradoxCascaded(2);
        given(objectMapper.convertValue(any(), eq(ParadoxCascaded.class))).willReturn(paradox);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.paradox-cascaded"))
                .willReturn(true);
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        consumer.handle(envelope);

        // then
        then(applicationEventPublisher).should().publishEvent(captor.capture());
        var collapsed = (TimelineCollapsed) captor.getValue();
        assertThat(collapsed.winners())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_2);
        assertThat(collapsed.losers())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactly(PLAYER_3);
    }

    // ─── wrong event type ─────────────────────────────────────────────────────

    @Test
    @DisplayName("unrelated event type — ignored, nothing happens")
    void handle_wrongEventType_ignored() {
        // given
        var envelope = EventEnvelope.create(GAME_ID, "Game", GAME_ID, 1, "unrelated");

        // when
        consumer.handle(envelope);

        // then
        then(gameRepository).should(never()).findById(any());
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("unsupported envelope version — skipped without claiming the eventId")
    void handle_unsupportedVersion_skippedWithoutClaim() {
        // given
        var envelope = EventEnvelope.create(GAME_ID, "Game", GAME_ID, 2, paradoxCascaded(1));

        // when
        consumer.handle(envelope);

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(gameRepository).should(never()).findById(any());
        then(eventPublisher).should(never()).publish(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("duplicate eventId — ignored without mutating game state")
    void handle_duplicateEventId_ignored() {
        // given
        var paradox = paradoxCascaded(1);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.paradox-cascaded"))
                .willReturn(false);

        // when
        consumer.handle(envelope);

        // then
        then(objectMapper).should(never()).convertValue(any(), eq(ParadoxCascaded.class));
        then(gameRepository).should(never()).findById(any());
        then(eventPublisher).should(never()).publish(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static ParadoxCascaded paradoxCascaded(int eraNumber) {
        return new ParadoxCascaded(GAME_ID, eraNumber, UUID.randomUUID(), UUID.randomUUID(), List.of());
    }

    private static EventEnvelope envelopeFor(ParadoxCascaded paradox) {
        return EventEnvelope.create(GAME_ID, "Game", GAME_ID, 1, paradox);
    }

    private static Lobby startedLobby(List<LobbyPlayer> players) {
        var config = new LobbyConfig("ABCD2345", 3, 5, java.time.Clock.systemUTC());
        return Lobby.reconstitute(
                LOBBY_ID, GAME_ID, players.getFirst().playerId(), players, LobbyStatus.STARTED, config);
    }
}
