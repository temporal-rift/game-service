package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.game.DrawnFutureEvent;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.CarryOverState;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.HandDealt;

@ExtendWith(MockitoExtension.class)
class EraSagaImplTest {

    public static final int DECK_SIZE = 30;
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
    SessionGameRulesPort gameRules;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    EraSagaImpl eraSaga;

    private static List<UUID> buildDeck(int size) {
        return new ArrayList<>(
                IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList());
    }

    private static FutureEventDefinition buildEventDef() {
        return buildEventDef(UUID.randomUUID());
    }

    private static FutureEventDefinition buildEventDef(UUID eventId) {
        return new FutureEventDefinition(
                eventId,
                "Test Event",
                List.of(
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Good", 40),
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Bad", 40),
                        new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Neutral", 20)));
    }

    private static DomainEventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    @Test
    @DisplayName("happy path — state set to RUNNING then WAITING_ROUND_1, events drawn and hands dealt")
    void start_happyPath_publishesEventsDrawnAndHandDealtThenAdvancesToWaitingRound1() {
        // given
        var deck = buildDeck(DECK_SIZE);
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
    @DisplayName("the same catalog card drawn into two different games gets different eventIds and outcomeIds")
    void start_sameCatalogCardDrawnTwice_yieldsDifferentEventIdsAndOutcomeIds() {
        // given — a single fixed catalog card, drawn independently into two different games
        var cardId = UUID.randomUUID();
        var catalogDef = buildEventDef(cardId);
        given(gameRules.eventsPerEra()).willReturn(1);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any())).willReturn(List.of(catalogDef));

        var gameA = new Game(GAME_ID, LOBBY_ID, buildDeck(1));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(gameA));
        var captorA = ArgumentCaptor.<DomainEventEnvelope>captor();
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());
        then(eventPublisher).should(atLeastOnce()).publish(captorA.capture());
        var eventA = eventsDrawnFrom(captorA).events().getFirst();

        var gameBId = UUID.randomUUID();
        var gameB = new Game(gameBId, LOBBY_ID, buildDeck(1));
        given(gameRepository.findById(gameBId)).willReturn(Optional.of(gameB));
        var captorB = ArgumentCaptor.<DomainEventEnvelope>captor();
        eraSaga.start(gameBId, ERA_NUMBER, PLAYER_IDS, List.of());
        then(eventPublisher).should(atLeastOnce()).publish(captorB.capture());
        var eventB = eventsDrawnFrom(captorB).events().getFirst();

        // then
        assertThat(eventA.eventId()).isNotEqualTo(eventB.eventId());
        assertThat(eventA.eventId()).isNotEqualTo(cardId);
        assertThat(eventB.eventId()).isNotEqualTo(cardId);
        var outcomeIdsA =
                eventA.outcomes().stream().map(EventsDrawn.Outcome::outcomeId).toList();
        var outcomeIdsB =
                eventB.outcomes().stream().map(EventsDrawn.Outcome::outcomeId).toList();
        assertThat(outcomeIdsA).doesNotContainAnyElementsOf(outcomeIdsB);
        var catalogOutcomeIds = catalogDef.outcomes().stream()
                .map(FutureEventDefinition.OutcomeDefinition::outcomeId)
                .toList();
        assertThat(outcomeIdsA).doesNotContainAnyElementsOf(catalogOutcomeIds);
    }

    private static EventsDrawn eventsDrawnFrom(ArgumentCaptor<DomainEventEnvelope> captor) {
        return captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof EventsDrawn)
                .map(e -> (EventsDrawn) e.payload())
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    @DisplayName("hand dealt carries correct player ID and card count")
    void start_happyPath_handDealtCarriesCorrectPlayerIdAndCardCount() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());

        var captor = ArgumentCaptor.<DomainEventEnvelope>captor();

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        then(eventPublisher).should(atLeastOnce()).publish(captor.capture());

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
    @DisplayName("dealt hands never contain STABILIZE or DETONATE — reactive Paradox Resolution-only cards")
    void start_happyPath_neverDealsStabilizeOrDetonateIntoTheActionHand() {
        // given — a large hand size makes a regression (drawing either card by chance) astronomically unlikely
        var largeHandSize = 2000;
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(largeHandSize);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());
        var captor = ArgumentCaptor.<DomainEventEnvelope>captor();

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        then(eventPublisher).should(atLeastOnce()).publish(captor.capture());
        var dealtCardTypes = captor.getAllValues().stream()
                .map(DomainEventEnvelope::payload)
                .filter(HandDealt.class::isInstance)
                .map(HandDealt.class::cast)
                .flatMap(hand -> hand.cards().stream())
                .map(HandDealt.CardInstance::cardType)
                .toList();
        assertThat(dealtCardTypes).isNotEmpty().doesNotContain(CardType.STABILIZE, CardType.DETONATE);
    }

    @Test
    @DisplayName("carry-over replaces a fresh draw without changing the cascade count")
    void start_withCarryOvers_drawsFewerFreshEventsAndPreservesTheirState() {
        // given — cascadedId and stalledId are the per-game eventIds these events were originally drawn with,
        // while cascadedCardId and stalledCardId are the catalog cards they came from, tracked separately on Game
        var cascadedId = UUID.randomUUID();
        var stalledId = UUID.randomUUID();
        var cascadedCardId = UUID.randomUUID();
        var stalledCardId = UUID.randomUUID();
        var cascadedOutcomeIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var stalledOutcomeIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE), 1, 1, GameStatus.IN_PROGRESS);
        game.recordDrawnEvents(Map.of(
                cascadedId, new DrawnFutureEvent(cascadedCardId, cascadedOutcomeIds),
                stalledId, new DrawnFutureEvent(stalledCardId, stalledOutcomeIds)));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(3);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        // drawn IDs come from the deck; carried-over cards are looked up by their catalog cardId
        given(futureEventCatalog.findByEventIds(
                        argThat(ids -> ids != null && !ids.contains(cascadedCardId) && !ids.contains(stalledCardId))))
                .willReturn(List.of(buildEventDef()));
        given(futureEventCatalog.findByEventIds(
                        argThat(ids -> ids != null && ids.contains(cascadedCardId) && ids.contains(stalledCardId))))
                .willReturn(List.of(buildEventDef(cascadedCardId), buildEventDef(stalledCardId)));

        var captor = ArgumentCaptor.<DomainEventEnvelope>captor();

        // when
        eraSaga.start(
                GAME_ID,
                ERA_NUMBER,
                PLAYER_IDS,
                List.of(
                        new PendingCarryOverEvent(cascadedId, CarryOverState.CASCADED),
                        new PendingCarryOverEvent(stalledId, CarryOverState.STALLED)));

        // then
        then(eventPublisher).should(atLeastOnce()).publish(captor.capture());
        var eventsDrawn = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof EventsDrawn)
                .map(e -> (EventsDrawn) e.payload())
                .findFirst()
                .orElseThrow();

        assertThat(eventsDrawn.events()).hasSize(3);
        assertThat(eventsDrawn.events())
                .extracting(EventsDrawn.FutureEvent::carryOverState)
                .containsExactly(CarryOverState.FRESH, CarryOverState.CASCADED, CarryOverState.STALLED);
        var cascadedEvent = eventsDrawn.events().stream()
                .filter(e -> e.carryOverState() == CarryOverState.CASCADED)
                .findFirst()
                .orElseThrow();
        assertThat(cascadedEvent.eventId()).isEqualTo(cascadedId);
        assertThat(cascadedEvent.outcomes())
                .extracting(EventsDrawn.Outcome::outcomeId)
                .containsExactlyElementsOf(cascadedOutcomeIds);
        assertThat(game.eventDeck()).hasSize(DECK_SIZE - 1);
        assertThat(game.cascadedParadoxCounter()).isEqualTo(1);
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
        assertThatNoException().isThrownBy(() -> eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of()));
    }

    @Test
    @DisplayName("happy path — EventsDrawn also published as typed Spring event for internal projections")
    void start_happyPath_publishesTypedEventsDrawn() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then — EventsDrawn + one HandDealt per player + StartActionRoundRequested
        then(applicationEventPublisher).should(times(4)).publishEvent(captor.capture());
        var eventsDrawn = captor.getAllValues().stream()
                .filter(EventsDrawn.class::isInstance)
                .map(EventsDrawn.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(eventsDrawn.gameId()).isEqualTo(GAME_ID);
        assertThat(eventsDrawn.eraNumber()).isEqualTo(ERA_NUMBER);
        assertThat(eventsDrawn.events()).isNotEmpty();
    }

    @Test
    @DisplayName("happy path — HandDealt also published as typed Spring event so the action module projects hands")
    void start_happyPath_publishesTypedHandDealtPerPlayer() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(gameRules.eventsPerEra()).willReturn(EVENTS_PER_ERA);
        given(gameRules.cardsPerHand()).willReturn(CARDS_PER_HAND);
        given(futureEventCatalog.findByEventIds(any()))
                .willReturn(IntStream.range(0, EVENTS_PER_ERA)
                        .mapToObj(i -> buildEventDef())
                        .toList());
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        eraSaga.start(GAME_ID, ERA_NUMBER, PLAYER_IDS, List.of());

        // then
        then(applicationEventPublisher).should(times(4)).publishEvent(captor.capture());
        var handsDealt = captor.getAllValues().stream()
                .filter(HandDealt.class::isInstance)
                .map(HandDealt.class::cast)
                .toList();
        assertThat(handsDealt).extracting(HandDealt::playerId).containsExactlyInAnyOrderElementsOf(PLAYER_IDS);
        assertThat(handsDealt).allSatisfy(hand -> assertThat(hand.cards()).hasSize(CARDS_PER_HAND));
    }

    @Test
    @DisplayName("state manager initRunning called before any event is published")
    void start_initRunningCalledFirst() {
        // given
        var game = new Game(GAME_ID, LOBBY_ID, buildDeck(DECK_SIZE));
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
}
