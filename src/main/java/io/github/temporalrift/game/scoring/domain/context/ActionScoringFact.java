package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

/**
 * An explicit scoring fact produced by a player action during the era.
 * Used for Activist, Revisionist, and Eraser rules that require explicit card plays.
 */
public record ActionScoringFact(UUID playerId, Faction faction, ScoreReason reason) {}
