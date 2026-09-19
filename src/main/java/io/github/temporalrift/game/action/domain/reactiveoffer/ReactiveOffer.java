package io.github.temporalrift.game.action.domain.reactiveoffer;

import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.shared.domain.AggregateRoot;
import io.github.temporalrift.game.shared.domain.model.CardType;

/**
 * One participant's private phase-opening reactive offer: exactly one Stabilize and one Detonate card
 * for a game and era. Offers are never part of ordinary dealing and never enter the five-card action
 * hand; a single accepted resolution submission consumes the offer, and unused offers expire when the
 * paradox-resolution phase closes.
 */
public class ReactiveOffer extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ReactiveOffer";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final UUID playerId;
    private final UUID stabilizeCardInstanceId;
    private final UUID detonateCardInstanceId;
    private UUID consumedCardInstanceId;
    private ReactiveOfferStatus status;

    public ReactiveOffer(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID stabilizeCardInstanceId,
            UUID detonateCardInstanceId) {
        this(
                id,
                gameId,
                eraNumber,
                playerId,
                stabilizeCardInstanceId,
                detonateCardInstanceId,
                null,
                ReactiveOfferStatus.OFFERED);
    }

    private ReactiveOffer(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID stabilizeCardInstanceId,
            UUID detonateCardInstanceId,
            UUID consumedCardInstanceId,
            ReactiveOfferStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.stabilizeCardInstanceId =
                Objects.requireNonNull(stabilizeCardInstanceId, "stabilizeCardInstanceId must not be null");
        this.detonateCardInstanceId =
                Objects.requireNonNull(detonateCardInstanceId, "detonateCardInstanceId must not be null");
        if (stabilizeCardInstanceId.equals(detonateCardInstanceId)) {
            throw new IllegalArgumentException("Offer cards must have distinct instance ids");
        }
        this.consumedCardInstanceId = consumedCardInstanceId;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static ReactiveOffer reconstitute(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID stabilizeCardInstanceId,
            UUID detonateCardInstanceId,
            UUID consumedCardInstanceId,
            ReactiveOfferStatus status) {
        return new ReactiveOffer(
                id,
                gameId,
                eraNumber,
                playerId,
                stabilizeCardInstanceId,
                detonateCardInstanceId,
                consumedCardInstanceId,
                status);
    }

    public CardType cardTypeOf(UUID cardInstanceId) {
        Objects.requireNonNull(cardInstanceId, "cardInstanceId must not be null");
        if (cardInstanceId.equals(stabilizeCardInstanceId)) {
            return CardType.STABILIZE;
        }
        if (cardInstanceId.equals(detonateCardInstanceId)) {
            return CardType.DETONATE;
        }
        throw new CardNotInHandException(cardInstanceId);
    }

    public CardType consume(UUID cardInstanceId) {
        var cardType = cardTypeOf(cardInstanceId);
        if (status != ReactiveOfferStatus.OFFERED) {
            throw new CardNotInHandException(cardInstanceId);
        }
        consumedCardInstanceId = cardInstanceId;
        status = ReactiveOfferStatus.CONSUMED;
        return cardType;
    }

    public void expire() {
        if (status == ReactiveOfferStatus.OFFERED) {
            status = ReactiveOfferStatus.EXPIRED;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public int eraNumber() {
        return eraNumber;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID stabilizeCardInstanceId() {
        return stabilizeCardInstanceId;
    }

    public UUID detonateCardInstanceId() {
        return detonateCardInstanceId;
    }

    public UUID consumedCardInstanceId() {
        return consumedCardInstanceId;
    }

    public ReactiveOfferStatus status() {
        return status;
    }
}
