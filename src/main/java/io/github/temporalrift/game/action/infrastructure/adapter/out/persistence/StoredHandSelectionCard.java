package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

record StoredHandSelectionCard(UUID cardInstanceId, String cardType, String cardGrade, int dealSlot) {
    static StoredHandSelectionCard fromDomain(HandDealt.CardInstance card) {
        return new StoredHandSelectionCard(
                card.cardInstanceId(), card.cardType().name(), card.grade().name(), card.dealSlot());
    }

    HandDealt.CardInstance toDomain() {
        return new HandDealt.CardInstance(
                cardInstanceId, CardType.valueOf(cardType), CardGrade.valueOf(cardGrade), dealSlot);
    }
}
