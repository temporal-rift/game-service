package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.PassParadoxResolutionUseCase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

/**
 * Records a player's paradox-resolution pass against this service's view of the phase, so a second
 * submission or pass is rejected and the status endpoint recovers it as submitted.
 *
 * <p>Nothing is published: timeline-service owns the phase and closes it early only once every player's
 * {@code ParadoxResolutionCardPlayed} has arrived, and the action-event contract has no fact for a pass
 * yet. Until it does, a phase with a passing player closes at its timer, which applies exactly what it
 * would for a player who never submitted.
 */
@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class})
class PassParadoxResolutionCommandHandler implements PassParadoxResolutionUseCase {

    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final PlayerStateRepository playerStateRepository;
    private final Clock clock;

    PassParadoxResolutionCommandHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
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
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }
}
