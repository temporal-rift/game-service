package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.CardType;

public record HandDealt(UUID gameId, int eraNumber, UUID playerId, List<CardInstance> cards) {

    public record CardInstance(UUID cardInstanceId, CardType cardType) {}
}
