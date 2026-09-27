package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/**
 * Scoring-relevant outcome facts for one event in a resolved era.
 *
 * @param writtenOutcomeId the outcome id that was written by the Prophet player, null if no Prophet wrote this event
 */
public record EventOutcomeFact(UUID eventId, UUID winningOutcomeId, UUID writtenOutcomeId) {}
