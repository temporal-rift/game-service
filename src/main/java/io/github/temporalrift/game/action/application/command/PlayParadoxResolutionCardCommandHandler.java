package io.github.temporalrift.game.action.application.command;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class})
class PlayParadoxResolutionCardCommandHandler implements PlayParadoxResolutionCardUseCase {

    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ActionTargetValidator actionTargetValidator;
    private final Clock clock;

    PlayParadoxResolutionCardCommandHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            ActionEventPublisher actionEventPublisher,
            ActionTargetValidator actionTargetValidator,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.actionTargetValidator = actionTargetValidator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        actionTargetValidator.validate(
                command.gameId(), command.eraNumber(), command.targetEventId(), command.targetOutcomeId());
        var phase = phaseRepository
                .findByGameIdAndEraNumberWithLock(command.gameId(), command.eraNumber())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotOpenException(command.gameId(), command.eraNumber()));
        var now = clock.instant();
        phase.assertPlayerCanSubmit(command.playerId(), now);

        var playerState = playerStateRepository
                .findByGameIdAndPlayerIdWithLock(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var submittedCard = playerState.hand().stream()
                .filter(card -> card.cardInstanceId().equals(command.cardInstanceId()))
                .findFirst()
                .orElseThrow(() -> new CardNotInHandException(command.cardInstanceId()));

        phase.submit(command.playerId(), submittedCard.cardType(), now);
        playerState.removeCard(command.cardInstanceId());
        phaseRepository.save(phase);
        playerStateRepository.save(playerState);
        publish(command, phase, submittedCard.cardType());
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }

    private void publish(
            Command command, ParadoxResolutionPhase phase, io.github.temporalrift.game.shared.CardType cardType) {
        var payload = new ParadoxResolutionCardPlayed(
                command.gameId(),
                command.eraNumber(),
                command.playerId(),
                command.cardInstanceId(),
                cardType,
                command.targetEventId(),
                command.targetOutcomeId());
        actionEventPublisher.publish(DomainEventEnvelope.create(
                phase.id(),
                ParadoxResolutionPhase.AGGREGATE_TYPE,
                command.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                payload,
                clock));
    }
}
