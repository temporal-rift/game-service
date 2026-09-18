package io.github.temporalrift.game.session.application.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.event.ForesightDeclared;
import io.github.temporalrift.game.shared.domain.event.ForesightRevealed;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

@ExtendWith(MockitoExtension.class)
class ForesightRevealListenerTest {

    @Mock
    GameRepository gameRepository;

    @Mock
    FutureEventCatalogPort catalog;

    @Mock
    ForesightRevealRepository reveals;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    SessionGameRulesPort gameRules;

    Clock clock = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);

    ForesightRevealListener listener;

    @BeforeEach
    void setUp() {
        listener = new ForesightRevealListener(gameRepository, catalog, reveals, eventPublisher, gameRules, clock);
    }

    private void givenStandardRules() {
        given(gameRules.maxEras()).willReturn(5);
    }

    private void givenDealRules() {
        given(gameRules.eventsPerEra()).willReturn(3);
    }

    @Test
    void onForesightDeclared_deliversTheDeckOrderedPreviewToTheViewerOnly() {
        givenStandardRules();
        givenDealRules();
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var deck = deck(6);
        var game = gameAtEra(gameId, deck, 2);
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer)).willReturn(Optional.empty());
        given(reveals.saveIfAbsent(any())).willReturn(true);
        given(catalog.findByEventIds(deck.subList(0, 3))).willReturn(definitions(deck.subList(0, 3)));

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), viewer));

        var reveal = ArgumentCaptor.forClass(ForesightReveal.class);
        then(reveals).should().saveIfAbsent(reveal.capture());
        assertThat(reveal.getValue().playerId()).isEqualTo(viewer);
        assertThat(reveal.getValue().nextEraNumber()).isEqualTo(3);
        assertThat(reveal.getValue().catalogEventIds()).containsExactlyElementsOf(deck.subList(0, 3));
        assertThat(reveal.getValue().emptyReason()).isNull();

        var envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(eventPublisher).should().publish(envelope.capture());
        var payload = (ForesightRevealed) envelope.getValue().payload();
        assertThat(payload.playerId()).isEqualTo(viewer);
        assertThat(payload.nextEraNumber()).isEqualTo(3);
        assertThat(payload.revealedEvents())
                .extracting(ForesightRevealed.RevealedEvent::catalogEventId)
                .containsExactlyElementsOf(deck.subList(0, 3));
        assertThat(envelope.getValue().gameId()).isEqualTo(gameId);

        then(gameRepository).should(never()).save(any());
        assertThat(game.eventDeck()).containsExactlyElementsOf(deck);
    }

    @Test
    void onForesightDeclared_inTheFinalEra_storesADefinedEmptyResult() {
        givenStandardRules();
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var game = gameAtEra(gameId, deck(3), 5);
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 5, viewer)).willReturn(Optional.empty());
        given(reveals.saveIfAbsent(any())).willReturn(true);

        listener.onForesightDeclared(new ForesightDeclared(gameId, 5, UUID.randomUUID(), UUID.randomUUID(), viewer));

        var reveal = ArgumentCaptor.forClass(ForesightReveal.class);
        then(reveals).should().saveIfAbsent(reveal.capture());
        assertThat(reveal.getValue().catalogEventIds()).isEmpty();
        assertThat(reveal.getValue().emptyReason()).isEqualTo("final-era");

        var envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(eventPublisher).should().publish(envelope.capture());
        var payload = (ForesightRevealed) envelope.getValue().payload();
        assertThat(payload.revealedEvents()).isEmpty();
        assertThat(payload.emptyReason()).isEqualTo("final-era");
        then(catalog).should(never()).findByEventIds(any());
    }

    @Test
    void onForesightDeclared_redelivered_revealsOnceAndLeavesTheDeckUnchanged() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var stored = new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), null);
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer)).willReturn(Optional.of(stored));

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), viewer));

        then(reveals).should(never()).saveIfAbsent(any());
        then(eventPublisher).should(never()).publish(any());
        then(gameRepository).should(never()).save(any());
    }

    @Test
    void onForesightDeclared_unknownGame_publishesNothing() {
        var gameId = UUID.randomUUID();
        given(gameRepository.findById(gameId)).willReturn(Optional.empty());

        listener.onForesightDeclared(
                new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        then(reveals).should(never()).saveIfAbsent(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    void revealedPreview_matchesTheSubsequentDraw() {
        givenStandardRules();
        givenDealRules();
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var deck = deck(6);
        var game = gameAtEra(gameId, deck, 2);
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer)).willReturn(Optional.empty());
        given(reveals.saveIfAbsent(any())).willReturn(true);
        given(catalog.findByEventIds(deck.subList(0, 3))).willReturn(definitions(deck.subList(0, 3)));

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), viewer));

        var reveal = ArgumentCaptor.forClass(ForesightReveal.class);
        then(reveals).should().saveIfAbsent(reveal.capture());
        var drawn = game.startEra(0, 3);
        assertThat(drawn).containsExactlyElementsOf(reveal.getValue().catalogEventIds());
    }

    @Test
    void onForesightDeclared_staleDeclarationForAPastEra_publishesNothing() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var game = gameAtEra(gameId, deck(6), 3);
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), viewer));

        then(reveals).should(never()).saveIfAbsent(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    void revealedPreview_matchesTheFreshPrefixWhenTheNextEraCarriesOver() {
        givenStandardRules();
        givenDealRules();
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var deck = deck(6);
        var game = gameAtEra(gameId, deck, 2);
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game));
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer)).willReturn(Optional.empty());
        given(reveals.saveIfAbsent(any())).willReturn(true);
        given(catalog.findByEventIds(deck.subList(0, 3))).willReturn(definitions(deck.subList(0, 3)));

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, UUID.randomUUID(), UUID.randomUUID(), viewer));

        var reveal = ArgumentCaptor.forClass(ForesightReveal.class);
        then(reveals).should().saveIfAbsent(reveal.capture());
        var drawnFresh = game.startEra(1, 3);
        assertThat(drawnFresh)
                .containsExactlyElementsOf(reveal.getValue().catalogEventIds().subList(0, 2));
    }

    private static Game gameAtEra(UUID gameId, List<UUID> deck, int eraNumber) {
        return Game.reconstitute(
                gameId, UUID.randomUUID(), new ArrayList<>(deck), eraNumber, 0, GameStatus.IN_PROGRESS);
    }

    private static List<UUID> deck(int size) {
        return IntStream.range(0, size).mapToObj(ignored -> UUID.randomUUID()).toList();
    }

    private static List<FutureEventDefinition> definitions(List<UUID> ids) {
        return ids.stream()
                .map(id -> new FutureEventDefinition(
                        id,
                        "Event " + id,
                        List.of(
                                new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Outcome A", 40),
                                new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Outcome B", 30),
                                new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Outcome C", 30))))
                .toList();
    }
}
