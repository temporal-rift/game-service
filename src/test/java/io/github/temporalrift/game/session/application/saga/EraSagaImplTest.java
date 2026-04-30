package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.action.HandDealt;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@ExtendWith(MockitoExtension.class)
class EraSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(UUID.randomUUID(), UUID.randomUUID());
    static final int ERA_NUMBER = 1;
    static final int EVENTS_PER_ERA = 3;
    static final int CARDS_PER_HAND = 5;

    @Mock
    GameRepository gameRepository;

    @Mock
    FutureEventCatalogPort futureEventCatalog;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    EraSagaStateManager stateManager;

    @Mock
    GameRulesPort gameRules;

    @InjectMocks
    EraSagaImpl eraSaga;

    @Test
    @DisplayName("happy path — state set to RUNNING then WAITING_ROUND_1, events drawn and hands dealt")
    void start_happyPath_publishesEventsDrawnAndHandDealtThenAdvancesToWaitingRound1() {
        // given
        var deck = buildDeck(30);
        var game = new Game(GAME_ID, LOBBY_ID, deck);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        var catalogDefs = IntStream.range(0, EVENTS_PER_ERA)
                .mapToObj(i -> buildEventDef())
                .toList();
        given(futureEventCatalog.findByEventIds(any())).willReturn(catalogDefs);

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        var ordered = inOrder(stateManager, eventPublisher);
        then(stateManager).should(ordered).initRunning(GAME_ID, ERA_NUMBER, PLAYER_IDS);
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(EventsDrawn.class));
        then(eventPublisher).should(ordered, times(2)).publish(envelopeWithPayload(HandDealt.class));
        then(stateManager).should(ordered).advanceTo(GAME_ID, EraSagaStatus.WAITING_ROUND_1);
    }

    @Test
    @DisplayName("hand dealt carries correct player ID and card count")
    void start_happyPath_handDealtCarriesCorrectPlayerIdAndCardCount() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(30));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());

        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        var handDealtEnvelopes = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof HandDealt)
                .toList();
        assertThat(handDealtEnvelopes).hasSize(2);

        var hand1 = (HandDealt) handDealtEnvelopes.get(0).payload();
        var hand2 = (HandDealt) handDealtEnvelopes.get(1).payload();
        assertThat(List.of(hand1.playerId(), hand2.playerId())).containsExactlyInAnyOrderElementsOf(PLAYER_IDS);
        assertThat(hand1.cards()).hasSize(CARDS_PER_HAND);
        assertThat(hand2.cards()).hasSize(CARDS_PER_HAND);
    }

    @Test
    @DisplayName("cascaded events are flagged isCascaded=true in EventsDrawn")
    void start_withCascadedEvents_cascadedFlagSetCorrectly() {
        // given
        var cascadedId = UUID.randomUUID();
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(30));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(3);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        // 2 drawn + 1 cascaded = 3 definitions returned
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(List.of(buildEventDef(), buildEventDef(), buildEventDef()));

        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of(cascadedId));

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        var eventsDrawn = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof EventsDrawn)
                .map(e -> (EventsDrawn) e.payload())
                .findFirst()
                .orElseThrow();

        assertThat(eventsDrawn.events()).hasSize(3);
        assertThat(eventsDrawn.events().get(0).isCascaded()).isFalse();
        assertThat(eventsDrawn.events().get(1).isCascaded()).isFalse();
        assertThat(eventsDrawn.events().get(2).isCascaded()).isTrue();
    }

    @Test
    @DisplayName("insufficient deck — saga marked FAILED, GameEndedAbnormally published, no WAITING_ROUND_1")
    void start_insufficientDeck_marksFailedAndPublishesGameEndedAbnormally() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, List.of()); // empty deck
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        then(stateManager).should().fail(GAME_ID);
        then(eventPublisher).should().publish(envelopeWithPayload(GameEndedAbnormally.class));
        then(stateManager).should(never()).advanceTo(any(), any());
    }

    @Test
    @DisplayName("insufficient deck — does not rethrow exception")
    void start_insufficientDeck_doesNotRethrow() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);

        // when / then — no exception expected
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());
    }

    @Test
    @DisplayName("state manager initRunning called before any event is published")
    void start_initRunningCalledFirst() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(30));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        var ordered = inOrder(stateManager, eventPublisher);
        then(stateManager).should(ordered).initRunning(any(), any(int.class), any());
        then(eventPublisher).should(ordered, atLeastOnce()).publish(any());
    }

    private static List<UUID> buildDeck(int size) {
        return new ArrayList<>(
                IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList());
    }

    private static FutureEventDefinition buildEventDef() {
        return new FutureEventDefinition(
                UUID.randomUUID(),
                "Test Event",
                List.of(
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Good", 40),
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Bad", 40),
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Neutral", 20)));
    }

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }
}
