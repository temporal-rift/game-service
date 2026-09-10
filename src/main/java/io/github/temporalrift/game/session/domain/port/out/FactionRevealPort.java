package io.github.temporalrift.game.session.domain.port.out;

import io.github.temporalrift.game.shared.FactionRevealed;

public interface FactionRevealPort {

    void reveal(FactionRevealed event);
}
