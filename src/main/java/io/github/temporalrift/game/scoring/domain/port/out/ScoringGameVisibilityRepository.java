package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

/** Stores whether faction names are visible for a game's scoring reads. */
public interface ScoringGameVisibilityRepository {

    boolean areFactionsRevealed(UUID gameId);

    void markFactionsRevealed(UUID gameId);
}
