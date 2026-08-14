package io.github.temporalrift.game.shared;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module deals a player's hand; the action module projects from it.
 * Lives in {@code game.shared} - the neutral shared kernel - so referencing it never creates a Spring
 * Modulith module cycle. See {@link EventsDrawn} for the rationale.
 */
public record HandDealt(
        UUID gameId, int eraNumber, UUID playerId, Instant selectionExpiresAt, List<CardInstance> cards) {

    public HandDealt(UUID gameId, int eraNumber, UUID playerId, List<CardInstance> cards) {
        this(gameId, eraNumber, playerId, Instant.EPOCH, cards);
    }

    public record CardInstance(UUID cardInstanceId, CardType cardType, CardGrade grade, int dealSlot) {

        public CardInstance(UUID cardInstanceId, CardType cardType, CardGrade grade) {
            this(cardInstanceId, cardType, grade, 0);
        }

        public CardInstance(UUID cardInstanceId, CardType cardType) {
            this(cardInstanceId, cardType, CardGrade.I, 0);
        }
    }
}
