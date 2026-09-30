package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered.EligibleCard;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParadoxResolutionCardsOffering")
class ParadoxResolutionCardsOfferingTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int ERA = 2;
    static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Mock
    ReactiveOfferRepository reactiveOfferRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    ParadoxResolutionCardsOffering offering;

    ParadoxResolutionPhase phase;

    @BeforeEach
    void setUp() {
        offering = new ParadoxResolutionCardsOffering(
                reactiveOfferRepository, actionEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        phase = new ParadoxResolutionPhase(UUID.randomUUID(), GAME_ID, ERA, NOW.plusSeconds(30));
    }

    @Test
    @DisplayName("offer — first time for the phase — deals the offer, then publishes the eligible hand and offer cards")
    void offerPublishesTheCompleteEligibleSet() {
        // given
        var suppress = new PlayerState.CardInstance(UUID.randomUUID(), CardType.SUPPRESS, CardGrade.III);
        var swing = new PlayerState.CardInstance(UUID.randomUUID(), CardType.SWING, CardGrade.I);
        given(reactiveOfferRepository.createIfAbsent(any())).willReturn(true);

        // when
        offering.offer(phase, participant(List.of(swing, suppress)));

        // then
        var dealt = ArgumentCaptor.forClass(ReactiveOffer.class);
        then(reactiveOfferRepository).should().createIfAbsent(dealt.capture());
        var offer = dealt.getValue();
        assertThat(offer.gameId()).isEqualTo(GAME_ID);
        assertThat(offer.eraNumber()).isEqualTo(ERA);
        assertThat(offer.playerId()).isEqualTo(PLAYER_ID);
        var expected = new ParadoxResolutionCardsOffered(
                GAME_ID,
                ERA,
                PLAYER_ID,
                List.of(
                        new EligibleCard(suppress.cardInstanceId(), CardType.SUPPRESS, CardGrade.III),
                        new EligibleCard(offer.stabilizeCardInstanceId(), CardType.STABILIZE, CardGrade.I),
                        new EligibleCard(offer.detonateCardInstanceId(), CardType.DETONATE, CardGrade.I)));
        then(actionEventPublisher)
                .should()
                .publish(argThat(envelope -> envelope.aggregateId().equals(phase.id())
                        && envelope.aggregateType().equals(ParadoxResolutionPhase.AGGREGATE_TYPE)
                        && envelope.gameId().equals(GAME_ID)
                        && envelope.occurredAt().equals(NOW)
                        && envelope.payload().equals(expected)));
        then(actionEventPublisher).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("offer — participant already dealt for the phase — publishes nothing")
    void offerAlreadyDealtPublishesNothing() {
        // given
        given(reactiveOfferRepository.createIfAbsent(any())).willReturn(false);

        // when
        offering.offer(phase, participant(List.of()));

        // then
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("eligibleCards — no eligible hand card and no offered card — is empty")
    void eligibleCardsCanBeEmpty() {
        var scan = new PlayerState.CardInstance(UUID.randomUUID(), CardType.SCAN, CardGrade.II);

        assertThat(ParadoxResolutionCardsOffering.eligibleCards(List.of(scan), List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("eligibleCards — offered cards — follow the eligible hand cards at grade I")
    void eligibleCardsListsHandCardsThenOfferedCards() {
        var push = new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.II);
        var detonate = new ReactiveOffer.EligibleCard(UUID.randomUUID(), CardType.DETONATE);

        assertThat(ParadoxResolutionCardsOffering.eligibleCards(List.of(push), List.of(detonate)))
                .containsExactly(
                        new EligibleCard(push.cardInstanceId(), CardType.PUSH, CardGrade.II),
                        new EligibleCard(detonate.cardInstanceId(), CardType.DETONATE, CardGrade.I));
    }

    private static PlayerState participant(List<PlayerState.CardInstance> hand) {
        return PlayerState.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                PLAYER_ID,
                Faction.WEAVERS,
                new PlayerState.PersistedState(hand, Set.of(), false, false));
    }
}
