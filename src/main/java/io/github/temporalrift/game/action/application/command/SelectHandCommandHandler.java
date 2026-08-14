package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.SelectHandUseCase;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionNotOpenException;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;

@Service
class SelectHandCommandHandler implements SelectHandUseCase {
    private final HandSelectionRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SelectHandCommandHandler(HandSelectionRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var selection = repository
                .findByGameIdAndEraNumberAndPlayerIdWithLock(command.gameId(), command.eraNumber(), command.playerId())
                .orElseThrow(() ->
                        new HandSelectionNotOpenException(command.gameId(), command.eraNumber(), command.playerId()));
        var resolved = selection.select(command.keptCardInstanceIds(), clock.instant());
        repository.save(resolved);
        events.publishEvent(resolved.toEvent());
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }
}
