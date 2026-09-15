package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Cross-module fact for an Activist declaration of record.
 *
 * <p>This in-process projection event deliberately carries only the public declaration fields. It
 * is separate from the action Kafka payload so the scoring and session modules do not depend on
 * action-domain types.
 */
public record ActivistDeclarationRecorded(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        SpecialAction mode,
        UUID targetEventId,
        UUID targetOutcomeId) {}
