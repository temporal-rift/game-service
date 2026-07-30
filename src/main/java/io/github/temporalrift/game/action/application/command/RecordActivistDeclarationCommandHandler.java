package io.github.temporalrift.game.action.application.command;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidSpecialActionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

@Service
class RecordActivistDeclarationCommandHandler implements RecordActivistDeclarationUseCase {

    private static final int DECLARATION_ROUND_NUMBER = 1;

    private final ActivistEraStateRepository activistEraStateRepository;
    private final ActionRoundRepository actionRoundRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionTargetValidator actionTargetValidator;
    private final ActionEventPublisher actionEventPublisher;
    private final Clock clock;

    RecordActivistDeclarationCommandHandler(
            ActivistEraStateRepository activistEraStateRepository,
            ActionRoundRepository actionRoundRepository,
            PlayerStateRepository playerStateRepository,
            ActionTargetValidator actionTargetValidator,
            ActionEventPublisher actionEventPublisher,
            Clock clock) {
        this.activistEraStateRepository = activistEraStateRepository;
        this.actionRoundRepository = actionRoundRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionTargetValidator = actionTargetValidator;
        this.actionEventPublisher = actionEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        actionTargetValidator.validate(
                command.gameId(), command.eraNumber(), command.targetEventId(), command.targetOutcomeId());
        if (actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(command.gameId(), command.eraNumber(), DECLARATION_ROUND_NUMBER)
                .isPresent()) {
            throw new DeclarationWindowClosedException(command.gameId(), command.eraNumber());
        }
        var playerState = playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        validateActivist(playerState.faction(), playerState.isJammed(), command.playerId(), command.mode());
        var state = activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(command.gameId(), command.eraNumber(), command.playerId())
                .orElseGet(() -> new ActivistEraState(
                        UUID.randomUUID(),
                        command.gameId(),
                        command.eraNumber(),
                        command.playerId(),
                        previousDeclarationSucceeded(command)));
        state.declare(command.mode(), command.targetEventId(), command.targetOutcomeId());
        activistEraStateRepository.save(state);
        publishDeclarationRecorded(state);
        return new Result(
                command.gameId(),
                command.eraNumber(),
                command.playerId(),
                command.mode(),
                command.targetEventId(),
                command.targetOutcomeId());
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

    private void publishDeclarationRecorded(ActivistEraState state) {
        var externalEvent = new ActivistDeclarationRecorded(
                state.gameId(),
                state.eraNumber(),
                DECLARATION_ROUND_NUMBER,
                state.activistPlayerId(),
                state.declarationMode(),
                state.targetEventId(),
                state.targetOutcomeId());
        actionEventPublisher.publish(DomainEventEnvelope.create(
                state.id(),
                ActivistEraState.AGGREGATE_TYPE,
                state.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                externalEvent,
                clock));
        actionEventPublisher.publishInternally(new io.github.temporalrift.game.shared.ActivistDeclarationRecorded(
                state.gameId(),
                state.eraNumber(),
                DECLARATION_ROUND_NUMBER,
                state.activistPlayerId(),
                state.declarationMode()
                                == io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode
                                        .RALLY
                        ? SpecialAction.RALLY
                        : SpecialAction.MOMENTUM,
                state.targetEventId(),
                state.targetOutcomeId()));
    }

    private void validateActivist(
            Faction faction,
            boolean jammed,
            UUID playerId,
            io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode mode) {
        if (jammed) {
            throw new JammedPlayerException(playerId);
        }
        if (faction == null) {
            throw new FactionRequiredException(playerId);
        }
        var specialAction =
                mode == io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode.RALLY
                        ? SpecialAction.RALLY
                        : SpecialAction.MOMENTUM;
        if (faction != Faction.ACTIVISTS) {
            throw new InvalidSpecialActionException(faction, specialAction);
        }
    }
}
