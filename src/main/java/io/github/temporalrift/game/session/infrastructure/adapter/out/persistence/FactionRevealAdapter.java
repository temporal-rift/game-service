package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.FactionRevealCommand;
import io.github.temporalrift.game.session.domain.port.out.FactionRevealPort;
import io.github.temporalrift.game.shared.FactionRevealed;

@Component
class FactionRevealAdapter implements FactionRevealPort {

    private final FactionRevealCommand factionRevealCommand;

    FactionRevealAdapter(FactionRevealCommand factionRevealCommand) {
        this.factionRevealCommand = factionRevealCommand;
    }

    @Override
    public void reveal(FactionRevealed event) {
        factionRevealCommand.reveal(event);
    }
}
