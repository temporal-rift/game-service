package io.github.temporalrift.game.action.application.command;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.ActivistMomentumEligibility;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidSpecialActionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;

@Service
class RecordActivistDeclarationCommandHandler implements RecordActivistDeclarationUseCase {

    private static final int DECLARATION_ROUND_NUMBER = 1;

    private final ActivistEraStateRepository activistEraStateRepository;
    private final ActionRoundRepository actionRoundRepository;
    private final DeclarationPhaseRepository declarationPhaseRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionTargetValidator actionTargetValidator;
    private final ActivistMomentumEligibility momentumEligibility;
    private final ActionEventPublisher actionEventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEvents;

    RecordActivistDeclarationCommandHandler(
            ActivistEraStateRepository activistEraStateRepository,
            ActionRoundRepository actionRoundRepository,
            DeclarationPhaseRepository declarationPhaseRepository,
            PlayerStateRepository playerStateRepository,
            ActionTargetValidator actionTargetValidator,
            ActivistMomentumEligibility momentumEligibility,
            ActionEventPublisher actionEventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.activistEraStateRepository = activistEraStateRepository;
        this.actionRoundRepository = actionRoundRepository;
        this.declarationPhaseRepository = declarationPhaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionTargetValidator = actionTargetValidator;
        this.momentumEligibility = momentumEligibility;
        this.actionEventPublisher = actionEventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.clock = clock;
        this.applicationEvents = applicationEventPublisher;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        actionTargetValidator.validate(
                command.gameId(), command.eraNumber(), command.targetEventId(), command.targetOutcomeId());
        var playerState = playerStateRepository
                .findByGameIdAndPlayerIdWithLock(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var phase = declarationPhaseRepository
                .findByGameIdAndEraNumberWithLock(command.gameId(), command.eraNumber())
                .orElseThrow(() -> new DeclarationWindowClosedException(command.gameId(), command.eraNumber()));
        phase.assertOpen(clock.instant());
        if (actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(command.gameId(), command.eraNumber(), DECLARATION_ROUND_NUMBER)
                .isPresent()) {
            throw new DeclarationWindowClosedException(command.gameId(), command.eraNumber());
        }
        validateActivist(playerState.faction(), playerState.isJammed(), command.playerId(), command.mode());
        phase.assertPending(command.playerId(), clock.instant());
        var state = activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(command.gameId(), command.eraNumber(), command.playerId())
                .orElseGet(() -> new ActivistEraState(
                        UUID.randomUUID(),
                        command.gameId(),
                        command.eraNumber(),
                        command.playerId(),
                        momentumEligibility.isEligible(command.gameId(), command.eraNumber(), command.playerId())));
        state.declare(command.mode(), command.targetEventId(), command.targetOutcomeId());
        activistEraStateRepository.save(state);
        publishDeclarationRecorded(state);
        var closed = phase.decide(command.playerId(), DeclarationDecision.DECLARED, clock.instant());
        declarationPhaseRepository.save(phase);
        if (closed) {
            applicationEvents.publishEvent(new DeclarationPhaseClosed(command.gameId(), command.eraNumber()));
        }
        return new Result(
                command.gameId(),
                command.eraNumber(),
                command.playerId(),
                command.mode(),
                command.targetEventId(),
                command.targetOutcomeId());
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
        DomainEventEnvelope<ActionEventPayload> envelope = DomainEventEnvelope.create(
                state.id(),
                ActivistEraState.AGGREGATE_TYPE,
                state.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                externalEvent,
                clock);
        sagaHandoffPublisher.publish(
                actionEventPublisher::publish,
                envelope,
                new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded(
                        state.gameId(),
                        state.eraNumber(),
                        DECLARATION_ROUND_NUMBER,
                        state.activistPlayerId(),
                        state.declarationMode().toSpecialAction(),
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
        var specialAction = mode.toSpecialAction();
        if (faction != Faction.ACTIVISTS) {
            throw new InvalidSpecialActionException(faction, specialAction);
        }
    }
}
