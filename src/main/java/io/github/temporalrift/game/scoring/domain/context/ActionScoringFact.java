package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.Faction;

/**
 * An explicit scoring fact produced by a player action during the era.
 * Used for Activist, Revisionist, and Eraser rules that require explicit card plays.
 */
public record ActionScoringFact(UUID playerId, Faction faction, ScoreReason reason) {}
