package io.github.temporalrift.game.action.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import io.github.temporalrift.game.action.application.port.in.SubmitRoundActionUseCase;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

/** Routes each kind of round submission to the handler that owns its rules. */
@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class SubmitRoundActionCommandHandler implements SubmitRoundActionUseCase {

    private final PlayCardCommandHandler playCard;
    private final PlaySpecialActionCommandHandler playSpecial;
    private final PassActionRoundCommandHandler pass;

    SubmitRoundActionCommandHandler(
            PlayCardCommandHandler playCard,
            PlaySpecialActionCommandHandler playSpecial,
            PassActionRoundCommandHandler pass) {
        this.playCard = playCard;
        this.playSpecial = playSpecial;
        this.pass = pass;
    }

    @Override
    public Result handle(Command command) {
        return switch (command) {
            case PlayCard card -> playCard.handle(card);
            case PlaySpecial special -> playSpecial.handle(special);
            case Pass passing -> pass.handle(passing);
        };
    }
}
