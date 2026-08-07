package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionRoundEventPublication;
import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ExposeTargetNotEligibleException;
import io.github.temporalrift.game.action.domain.activisterastate.ExposeUnavailableException;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class PlaySpecialActionCommandHandler implements PlaySpecialActionUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ActivistEraStateRepository activistEraStateRepository;

    private final ActionEventPublisher actionEventPublisher;

    private final ActionTargetValidator actionTargetValidator;

    private final Clock clock;

    PlaySpecialActionCommandHandler(
            ActionRoundRepository actionRoundRepository,
            PlayerStateRepository playerStateRepository,
            ActivistEraStateRepository activistEraStateRepository,
            ActionEventPublisher actionEventPublisher,
            ActionTargetValidator actionTargetValidator,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
        this.activistEraStateRepository = activistEraStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.actionTargetValidator = actionTargetValidator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        actionTargetValidator.validate(
                command.gameId(), command.eraNumber(), command.targetEventId(), command.targetOutcomeId());
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
        if (command.specialAction() == SpecialAction.EXPOSE && faction == Faction.ACTIVISTS) {
            recordExpose(command);
        }
        if (command.specialAction() == SpecialAction.CORRUPT) {
            requireGameOpponent(command);
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
        ActionRoundEventPublication.publish(round, actionEventPublisher, clock);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }

    // ActionRound.submitSpecial only rejects a null or self targetPlayerId — it has no visibility into
    // the game's full player roster (it only tracks pendingPlayerIds, which shrinks as the round
    // progresses), so it cannot verify the target is an actual opponent in this game. Checked here,
    // where PlayerStateRepository has that visibility, mirroring how recordExpose already validates
    // its own targetPlayerId via round-1 submission membership.
    private void requireGameOpponent(Command command) {
        var targetPlayerId = command.targetPlayerId();
        if (targetPlayerId == null) {
            return;
        }
        playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), targetPlayerId)
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), targetPlayerId));
    }

    private void recordExpose(Command command) {
        if (command.roundNumber() != 2) {
            throw new ExposeUnavailableException();
        }
        var targetPlayerId = command.targetPlayerId();
        var signature = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(command.gameId(), command.eraNumber(), 1)
                .flatMap(round -> round.submittedActions().stream()
                        .filter(action -> action.playerId().equals(targetPlayerId))
                        .findFirst()
                        .flatMap(ProbabilityInfluenceSignature::from))
                .orElseThrow(() -> new ExposeTargetNotEligibleException(targetPlayerId));
        var state = activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(command.gameId(), command.eraNumber(), command.playerId())
                .orElseGet(() -> new ActivistEraState(
                        java.util.UUID.randomUUID(),
                        command.gameId(),
                        command.eraNumber(),
                        command.playerId(),
                        previousDeclarationSucceeded(command)));
        state.expose(targetPlayerId, signature);
        activistEraStateRepository.save(state);
    }

    private boolean previousDeclarationSucceeded(Command command) {
        if (command.eraNumber() == 1) {
            return false;
        }
        return activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(
                        command.gameId(), command.eraNumber() - 1, command.playerId())
                .map(ActivistEraState::declarationSucceeded)
                .orElse(false);
    }
}
