package io.github.temporalrift.game.action.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import io.github.temporalrift.game.action.application.port.in.SubmitParadoxResolutionUseCase;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;

/** Routes each kind of paradox-resolution submission to the handler that owns its rules. */
@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class, ReactiveOfferRepository.class})
class SubmitParadoxResolutionCommandHandler implements SubmitParadoxResolutionUseCase {

    private final PlayParadoxResolutionCardCommandHandler playCard;
    private final PassParadoxResolutionCommandHandler pass;

    SubmitParadoxResolutionCommandHandler(
            PlayParadoxResolutionCardCommandHandler playCard, PassParadoxResolutionCommandHandler pass) {
        this.playCard = playCard;
        this.pass = pass;
    }

    @Override
    public Result handle(Command command) {
        return switch (command) {
            case PlayCard card -> playCard.handle(card);
            case Pass passing -> pass.handle(passing);
        };
    }
}
