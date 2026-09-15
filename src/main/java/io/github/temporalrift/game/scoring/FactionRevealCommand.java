package io.github.temporalrift.game.scoring;

import io.github.temporalrift.game.shared.domain.event.FactionRevealed;

public interface FactionRevealCommand {

    void reveal(FactionRevealed event);
}
