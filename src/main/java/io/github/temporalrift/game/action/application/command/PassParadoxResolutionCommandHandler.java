package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.PassParadoxResolutionUseCase;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionPassed;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * Records a player's paradox-resolution pass against this service's view of the phase, so a second
 * submission or pass is rejected and the status endpoint recovers it as submitted, then publishes the
 * private {@link ParadoxResolutionPassed} fact. timeline-service owns the phase and counts that fact toward
 * closing it once every player has submitted or passed; it applies nothing for the passer at close.
 */
@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class})
class PassParadoxResolutionCommandHandler implements PassParadoxResolutionUseCase {

    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final Clock clock;

    PassParadoxResolutionCommandHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var phase = phaseRepository
                .findByGameIdAndEraNumberWithLock(command.gameId(), command.eraNumber())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotOpenException(command.gameId(), command.eraNumber()));
        playerStateRepository
                .findByGameIdAndPlayerId(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        phase.pass(command.playerId(), clock.instant());
        phaseRepository.save(phase);
        actionEventPublisher.publish(DomainEventEnvelope.create(
                phase.id(),
                ParadoxResolutionPhase.AGGREGATE_TYPE,
                command.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new ParadoxResolutionPassed(command.gameId(), command.eraNumber(), command.playerId()),
                clock));
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }
}
