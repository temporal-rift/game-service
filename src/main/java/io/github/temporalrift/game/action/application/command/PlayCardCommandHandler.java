package io.github.temporalrift.game.action.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@Service
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class PlayCardCommandHandler implements PlayCardUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ActionEventPublisher actionEventPublisher;

    PlayCardCommandHandler(
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
        var playerHand = playerState.hand().stream()
                .map(PlayerState.CardInstance::cardInstanceId)
                .toList();
        var submittedCard = playerState.hand().stream()
                .filter(card -> card.cardInstanceId().equals(command.cardInstanceId()))
                .findFirst()
                .orElseThrow(() -> new CardNotInHandException(command.cardInstanceId()));
        var allSubmitted = round.submitCard(
                command.playerId(),
                command.cardInstanceId(),
                submittedCard.cardType(),
                command.targetEventId(),
                command.sourceOutcomeId(),
                command.targetOutcomeId(),
                playerHand);
        playerState.removeCard(command.cardInstanceId());
        actionRoundRepository.save(round);
        playerStateRepository.save(playerState);
        publishRoundEvents(round);

        return new Result(
                command.gameId(), command.eraNumber(), command.roundNumber(), command.playerId(), allSubmitted);
    }

    private void publishRoundEvents(ActionRound round) {
        // Card submissions need both publication paths: the outbox envelope for cross-service delivery
        // and the typed payload for the in-process action-round saga listener.
        for (var payload : round.pullEvents()) {
            publishEvent(round, payload);
            actionEventPublisher.publishInternally(payload);
        }
    }

    private void publishEvent(ActionRound round, Object payload) {
        switch (payload) {
            case ActionEventPayload actionEvent ->
                actionEventPublisher.publish(DomainEventEnvelope.create(
                        round.id(), ActionRound.AGGREGATE_TYPE, round.gameId(), 1, actionEvent));
            case ActionRoundClosed roundClosed ->
                actionEventPublisher.publishRoundClosed(DomainEventEnvelope.create(
                        round.id(), ActionRound.AGGREGATE_TYPE, round.gameId(), 1, roundClosed));
            default -> throw new IllegalStateException("Unsupported action aggregate event: " + payload.getClass());
        }
    }
}
