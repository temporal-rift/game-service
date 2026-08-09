package io.github.temporalrift.game.scoring.application.command;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

record PlayerScoreDecision(UUID playerId, ScoreReason reason, int eraNumber, int multiplier) {

    PlayerScoreDecision(UUID playerId, ScoreReason reason, int eraNumber) {
        this(playerId, reason, eraNumber, 1);
    }
}
