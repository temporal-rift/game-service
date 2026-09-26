package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.GameParticipantValidator;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class PlayCardCommandHandler implements PlayCardUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ActionEventPublisher actionEventPublisher;

    private final ActionTargetValidator actionTargetValidator;

    private final GameParticipantValidator gameParticipantValidator;

    private final GameRulesPort gameRules;

    private final Clock clock;

    PlayCardCommandHandler(
            ActionRoundRepository actionRoundRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            ActionTargetValidator actionTargetValidator,
            GameParticipantValidator gameParticipantValidator,
            GameRulesPort gameRules,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.actionTargetValidator = actionTargetValidator;
        this.gameParticipantValidator = gameParticipantValidator;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(
                        command.gameId(), command.eraNumber(), command.roundNumber())
                .orElseThrow(
                        () -> new RoundNotFoundException(command.gameId(), command.eraNumber(), command.roundNumber()));
        var playerState = playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var submittedCard = playerState.hand().stream()
                .filter(card -> card.cardInstanceId().equals(command.cardInstanceId()))
                .findFirst()
                .orElseThrow(() -> new CardNotInHandException(command.cardInstanceId()));
        var currentEraEventIds = actionTargetValidator.validateCardTargets(
                command.gameId(),
                command.eraNumber(),
                command.targetEventId(),
                command.targetEventIds(),
                command.sourceOutcomeId(),
                command.targetOutcomeId());
        var action = new SubmittedAction.CardAction(
                command.playerId(),
                command.cardInstanceId(),
                submittedCard.cardType(),
                submittedCard.grade(),
                command.targetEventId(),
                command.targetEventIds(),
                command.sourceOutcomeId(),
                command.targetOutcomeId(),
                command.targetPlayerId(),
                command.targetPlayerIds(),
                command.disguiseCategory());
        action.validateFinalEra(command.eraNumber(), command.roundNumber(), gameRules.maxEras());
        action.validateCurrentEraTargets(currentEraEventIds);
        actionTargetValidator.validateTraceTargetInPrecedingRound(
                command.gameId(),
                command.eraNumber(),
                command.roundNumber(),
                submittedCard.cardType(),
                command.targetEventId());
        gameParticipantValidator.requireParticipant(command.gameId(), command.targetPlayerId());
        gameParticipantValidator.requireParticipants(command.gameId(), command.targetPlayerIds());
        var allSubmitted = round.submit(action);
        playerState.removeCard(command.cardInstanceId());
        actionRoundRepository.save(round);
        playerStateRepository.save(playerState);
        ActionRoundEventPublication.publish(round, actionEventPublisher, clock);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }
}
