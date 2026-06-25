package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

/**
 * A chain scoring fact for a Weaver player in this era.
 * Reason is either CHAIN_COMPLETED, CHAIN_BROKEN, or CHAIN_LINK_ADDED.
 */
public record ChainScoringFact(UUID playerId, UUID chainId, ScoreReason reason) {}
