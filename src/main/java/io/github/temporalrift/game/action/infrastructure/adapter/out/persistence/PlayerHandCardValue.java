package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

@Embeddable
record PlayerHandCardValue(
        @Column(name = "card_instance_id", nullable = false) UUID cardInstanceId,
        @Column(name = "card_type", nullable = false) String cardType) {

    static PlayerHandCardValue fromDomain(PlayerState.CardInstance card) {
        return new PlayerHandCardValue(card.cardInstanceId(), card.cardType().name());
    }

    PlayerState.CardInstance toDomain() {
        return new PlayerState.CardInstance(cardInstanceId, CardType.valueOf(cardType));
    }
}
