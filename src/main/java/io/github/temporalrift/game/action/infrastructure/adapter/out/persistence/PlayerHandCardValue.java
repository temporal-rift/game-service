package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

@Embeddable
record PlayerHandCardValue(
        @Column(name = "card_instance_id", nullable = false) UUID cardInstanceId,
        @Column(name = "card_type", nullable = false) String cardType,
        @Column(name = "card_grade", nullable = false) String cardGrade) {

    PlayerHandCardValue(UUID cardInstanceId, String cardType) {
        this(cardInstanceId, cardType, CardGrade.I.name());
    }

    static PlayerHandCardValue fromDomain(PlayerState.CardInstance card) {
        return new PlayerHandCardValue(
                card.cardInstanceId(), card.cardType().name(), card.grade().name());
    }

    PlayerState.CardInstance toDomain() {
        return new PlayerState.CardInstance(
                cardInstanceId,
                CardType.valueOf(cardType),
                cardGrade == null ? CardGrade.I : CardGrade.valueOf(cardGrade));
    }
}
