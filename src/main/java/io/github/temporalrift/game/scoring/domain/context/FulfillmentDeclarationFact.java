package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/** A Prophet's Fulfillment declaration on a current-era event, pending comparison to its written outcome. */
public record FulfillmentDeclarationFact(UUID playerId, UUID targetEventId) {}
