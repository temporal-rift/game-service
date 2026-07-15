package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

/**
 * A chain scoring fact for a Weaver player.
 * Reason is either CHAIN_COMPLETED, CHAIN_BROKEN, or CHAIN_LINK_ADDED.
 * eraNumber is the era the underlying chain event actually happened in, which may differ from
 * the era of the scoring pass that consumes this fact.
 */
public record ChainScoringFact(UUID playerId, UUID chainId, ScoreReason reason, int eraNumber) {}
