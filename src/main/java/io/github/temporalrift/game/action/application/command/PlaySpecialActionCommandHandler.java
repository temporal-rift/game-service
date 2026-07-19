package io.github.temporalrift.game.action.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class PlaySpecialActionCommandHandler implements PlaySpecialActionUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ActionEventPublisher actionEventPublisher;

    PlaySpecialActionCommandHandler(
            ActionRoundRepository actionRoundRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher) {
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
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
        var faction = playerState.faction();
        if (faction == null) {
            throw new FactionRequiredException(command.playerId());
        }
        var allSubmitted = round.submitSpecial(
                command.playerId(),
                faction,
                command.specialAction(),
                command.targetEventId(),
                command.targetOutcomeId(),
                command.targetPlayerId(),
                playerState.isJammed());
        actionRoundRepository.save(round);
        ActionRoundEventPublication.publish(round, actionEventPublisher);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }
}
