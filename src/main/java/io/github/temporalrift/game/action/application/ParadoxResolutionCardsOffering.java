package io.github.temporalrift.game.action.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered.EligibleCard;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

/**
 * Deals a participant's private Stabilize + Detonate offer for a paradox-resolution phase and publishes their
 * {@link ParadoxResolutionCardsOffered} fact. Both happen only when the offer is newly created, so each participant
 * gets exactly one fact per phase however often the phase opening or their adoption is redelivered.
 */
@Component
public class ParadoxResolutionCardsOffering {

    private final ReactiveOfferRepository reactiveOfferRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ExecutionEntropy entropy;
    private final Clock clock;

    public ParadoxResolutionCardsOffering(
            ReactiveOfferRepository reactiveOfferRepository,
            ActionEventPublisher actionEventPublisher,
            ExecutionEntropy entropy,
            Clock clock) {
        this.reactiveOfferRepository = reactiveOfferRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.entropy = entropy;
        this.clock = clock;
    }

    public void offer(ParadoxResolutionPhase phase, PlayerState participant) {
        var playerId = participant.playerId();
        var offerCoordinate = EntropyCoordinate.none().era(phase.eraNumber()).player(playerId);
        var offer = new ReactiveOffer(
                UUID.randomUUID(),
                phase.gameId(),
                phase.eraNumber(),
                playerId,
                entropy.identity(IdentityKind.OFFERED_STABILIZE_CARD_INSTANCE, offerCoordinate),
                entropy.identity(IdentityKind.OFFERED_DETONATE_CARD_INSTANCE, offerCoordinate));
        if (!reactiveOfferRepository.createIfAbsent(offer)) {
            return;
        }
        actionEventPublisher.publish(DomainEventEnvelope.create(
                phase.id(),
                ParadoxResolutionPhase.AGGREGATE_TYPE,
                phase.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new ParadoxResolutionCardsOffered(
                        phase.gameId(),
                        phase.eraNumber(),
                        playerId,
                        eligibleCards(participant.hand(), offer.eligibleCards())),
                clock));
    }

    /**
     * The eligible resolution set: the hand cards whose type may resolve a paradox, then the still-offered reactive
     * cards, which are always grade I.
     */
    static List<EligibleCard> eligibleCards(
            List<PlayerState.CardInstance> hand, List<ReactiveOffer.EligibleCard> offeredCards) {
        return Stream.concat(
                        hand.stream()
                                .filter(card -> ParadoxResolutionPhase.ELIGIBLE_CARD_TYPES.contains(card.cardType()))
                                .map(card -> new EligibleCard(card.cardInstanceId(), card.cardType(), card.grade())),
                        offeredCards.stream()
                                .map(card -> new EligibleCard(card.cardInstanceId(), card.cardType(), CardGrade.I)))
                .toList();
    }
}
