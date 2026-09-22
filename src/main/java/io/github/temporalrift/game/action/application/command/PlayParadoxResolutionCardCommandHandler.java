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
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class, ReactiveOfferRepository.class})
class PlayParadoxResolutionCardCommandHandler implements PlayParadoxResolutionCardUseCase {

    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ReactiveOfferRepository reactiveOfferRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final ActionTargetValidator actionTargetValidator;
    private final Clock clock;

    PlayParadoxResolutionCardCommandHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            ReactiveOfferRepository reactiveOfferRepository,
            ActionEventPublisher actionEventPublisher,
            ActionTargetValidator actionTargetValidator,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.reactiveOfferRepository = reactiveOfferRepository;
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
        phase.assertAffectedEvent(command.targetEventId());

        var playerState = playerStateRepository
                .findByGameIdAndPlayerIdWithLock(command.gameId(), command.playerId())
                .orElseThrow(() -> new PlayerStateNotFoundException(command.gameId(), command.playerId()));
        var resolved = resolveCard(command, playerState);

        phase.submit(command.playerId(), resolved.cardType(), now);
        if (resolved.offer() != null && resolved.offer().containsCard(command.cardInstanceId())) {
            resolved.offer().consume(command.cardInstanceId());
            reactiveOfferRepository.save(resolved.offer());
        } else {
            playerState.removeCard(command.cardInstanceId());
            if (resolved.offer() != null) {
                resolved.offer().expire();
                reactiveOfferRepository.save(resolved.offer());
            }
        }
        phaseRepository.save(phase);
        playerStateRepository.save(playerState);
        publish(command, phase, resolved.cardType(), resolved.grade());
        return new Result(command.gameId(), command.eraNumber(), command.playerId());
    }

    /**
     * Resolves the submitted card against the final five-card hand first, then the player's
     * phase-opening reactive offer. A card in neither fails with card-not-in-hand; offers exist
     * only as dealt at phase opening, so no offer row is ever created on the submission path.
     */
    private ResolvedCard resolveCard(Command command, PlayerState playerState) {
        var offer = reactiveOfferRepository.findByGameIdAndEraNumberAndPlayerIdWithLock(
                command.gameId(), command.eraNumber(), command.playerId());
        var handCard = playerState.hand().stream()
                .filter(card -> card.cardInstanceId().equals(command.cardInstanceId()))
                .findFirst();
        if (handCard.isPresent()) {
            return new ResolvedCard(handCard.get().cardType(), handCard.get().grade(), offer.orElse(null));
        }
        var resolvedOffer = offer.orElseThrow(() -> new CardNotInHandException(command.cardInstanceId()));
        var cardType = resolvedOffer.cardTypeOf(command.cardInstanceId());
        return new ResolvedCard(cardType, CardGrade.I, resolvedOffer);
    }

    private record ResolvedCard(CardType cardType, CardGrade grade, ReactiveOffer offer) {}

    private void publish(Command command, ParadoxResolutionPhase phase, CardType cardType, CardGrade grade) {
        var payload = new ParadoxResolutionCardPlayed(
                command.gameId(),
                command.eraNumber(),
                command.playerId(),
                command.cardInstanceId(),
                cardType,
                grade,
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
