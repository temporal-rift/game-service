package io.github.temporalrift.game.session;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.CardType;

/**
 * Public cross-module event published by the session module. Lives in session's top-level package
 * on purpose, following the same convention as {@link io.github.temporalrift.game.action.StartActionRoundRequested}
 * so other modules may reference it without reaching into session's internal {@code domain.event} package.
 */
public record HandDealt(UUID gameId, int eraNumber, UUID playerId, List<CardInstance> cards) {

    public record CardInstance(UUID cardInstanceId, CardType cardType) {}
}
