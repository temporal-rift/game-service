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
import io.github.temporalrift.events.session.TimelineCollapsed;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.timeline.ParadoxCascaded;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;
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
    StartGameSagaRepository startGameSagaRepository;

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
        given(processedEventRepository.tryMarkProcessed(eq(envelope.eventId()), eq("ParadoxCascadedKafkaConsumer")))
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
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        var paradox = paradoxCascaded(2);
        given(objectMapper.convertValue(any(), eq(ParadoxCascaded.class))).willReturn(paradox);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(eq(envelope.eventId()), eq("ParadoxCascadedKafkaConsumer")))
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
        var assignments = List.of(
                new FactionAssignment(PLAYER_1, Faction.ERASERS),
                new FactionAssignment(PLAYER_2, Faction.REVISIONISTS),
                new FactionAssignment(PLAYER_3, Faction.PROPHETS));
        given(startGameSagaRepository.findByGameId(GAME_ID))
                .willReturn(Optional.of(startGameSagaStateWithAssignments(assignments)));
        var paradox = paradoxCascaded(2);
        given(objectMapper.convertValue(any(), eq(ParadoxCascaded.class))).willReturn(paradox);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(eq(envelope.eventId()), eq("ParadoxCascadedKafkaConsumer")))
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
    @DisplayName("duplicate eventId — ignored without mutating game state")
    void handle_duplicateEventId_ignored() {
        // given
        var paradox = paradoxCascaded(1);
        var envelope = envelopeFor(paradox);
        given(processedEventRepository.tryMarkProcessed(eq(envelope.eventId()), eq("ParadoxCascadedKafkaConsumer")))
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

    private StartGameSagaState startGameSagaState() {
        return startGameSagaStateWithAssignments(List.of(
                new FactionAssignment(PLAYER_1, Faction.PROPHETS), new FactionAssignment(PLAYER_2, Faction.WEAVERS)));
    }

    private StartGameSagaState startGameSagaStateWithAssignments(List<FactionAssignment> assignments) {
        return new StartGameSagaState(UUID.randomUUID(), GAME_ID, LOBBY_ID, StartGameSagaStatus.COMPLETED, assignments);
    }
}
