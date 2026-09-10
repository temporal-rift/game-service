package io.github.temporalrift.game.scoring;

import io.github.temporalrift.game.shared.FactionRevealed;

public interface FactionRevealCommand {

    void reveal(FactionRevealed event);
}
