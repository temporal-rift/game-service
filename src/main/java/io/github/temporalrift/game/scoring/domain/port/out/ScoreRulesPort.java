package io.github.temporalrift.game.scoring.domain.port.out;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public interface ScoreRulesPort {

    int pointsDelta(ScoreReason reason);
}
