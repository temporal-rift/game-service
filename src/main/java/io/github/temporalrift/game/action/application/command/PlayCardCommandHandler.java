package io.github.temporalrift.game.action.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class PlayCardCommandHandler implements PlayCardUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ActionEventPublisher actionEventPublisher;

    private final ActionTargetValidator actionTargetValidator;

    PlayCardCommandHandler(
            ActionRoundRepository actionRoundRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            ActionTargetValidator actionTargetValidator) {
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.actionTargetValidator = actionTargetValidator;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        actionTargetValidator.validate(
                command.gameId(),
                command.eraNumber(),
                command.targetEventId(),
                command.sourceOutcomeId(),
                command.targetOutcomeId());
        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(
                        command.gameId(), command.eraNumber(), command.roundNumber())
                .orElseThrow(
                        () -> new RoundNotFoundException(command.gameId(), command.eraNumber(), command.roundNumber()));
        var playerState = playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var playerHand = playerState.hand().stream()
                .map(PlayerState.CardInstance::cardInstanceId)
                .toList();
        var submittedCard = playerState.hand().stream()
                .filter(card -> card.cardInstanceId().equals(command.cardInstanceId()))
                .findFirst()
                .orElseThrow(() -> new CardNotInHandException(command.cardInstanceId()));
        var allSubmitted = round.submitCard(
                command.playerId(),
                command.cardInstanceId(),
                submittedCard.cardType(),
                command.targetEventId(),
                command.sourceOutcomeId(),
                command.targetOutcomeId(),
                playerHand);
        playerState.removeCard(command.cardInstanceId());
        actionRoundRepository.save(round);
        playerStateRepository.save(playerState);
        ActionRoundEventPublication.publish(round, actionEventPublisher);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }
}
