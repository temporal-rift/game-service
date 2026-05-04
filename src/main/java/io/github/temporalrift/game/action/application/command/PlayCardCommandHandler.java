package io.github.temporalrift.game.action.application.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Service
class PlayCardCommandHandler implements PlayCardUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    PlayCardCommandHandler(ActionRoundRepository actionRoundRepository, PlayerStateRepository playerStateRepository) {
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(command.gameId(), command.eraNumber(), command.roundNumber())
                .orElseThrow(
                        () -> new RoundNotFoundException(command.gameId(), command.eraNumber(), command.roundNumber()));
        var playerState = playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var playerHand = playerState.hand().stream()
                .map(PlayerState.CardInstance::cardInstanceId)
                .toList();
        var allSubmitted = round.submitCard(
                command.playerId(),
                command.cardInstanceId(),
                command.cardType(),
                command.targetEventId(),
                command.targetOutcomeId(),
                playerHand);
        playerState.removeCard(command.cardInstanceId());
        if (allSubmitted) {
            round.close("ALL_SUBMITTED");
        }
        actionRoundRepository.save(round);
        playerStateRepository.save(playerState);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }
}
