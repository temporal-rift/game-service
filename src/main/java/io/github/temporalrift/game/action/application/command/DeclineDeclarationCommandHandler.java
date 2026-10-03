package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidSpecialActionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@Service
class DeclineDeclarationCommandHandler implements DeclineDeclarationUseCase {
    private final PlayerStateRepository players;
    private final DeclarationPhaseRepository phases;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DeclineDeclarationCommandHandler(
            PlayerStateRepository players,
            DeclarationPhaseRepository phases,
            ApplicationEventPublisher events,
            Clock clock) {
        this.players = players;
        this.phases = phases;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var player = players.findByGameIdAndPlayerIdWithLock(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var phase = phases.findByGameIdAndEraNumberWithLock(command.gameId(), command.eraNumber())
                .orElseThrow(() -> new DeclarationWindowClosedException(command.gameId(), command.eraNumber()));
        if (phase.hasDeclined(command.playerId())) {
            return new Result(command.gameId(), command.eraNumber(), command.playerId());
        }
        if (player.faction() == null) {
            throw new FactionRequiredException(command.playerId());
        }
        if (player.faction() != Faction.ACTIVISTS) {
            throw new InvalidSpecialActionException(player.faction(), SpecialAction.RALLY);
        }
        if (player.isJammed()) {
            throw new JammedPlayerException(command.playerId());
        }
        var closed = phase.decide(command.playerId(), DeclarationDecision.DECLINED, clock.instant());
        phases.save(phase);
        if (closed) {
            events.publishEvent(new DeclarationPhaseClosed(command.gameId(), command.eraNumber()));
        }
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }
}
