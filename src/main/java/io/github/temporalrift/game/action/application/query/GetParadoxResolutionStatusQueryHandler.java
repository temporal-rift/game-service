package io.github.temporalrift.game.action.application.query;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.GetParadoxResolutionStatusUseCase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotFoundException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOfferStatus;
import io.github.temporalrift.game.shared.domain.model.CardGrade;

@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class, ReactiveOfferRepository.class})
class GetParadoxResolutionStatusQueryHandler implements GetParadoxResolutionStatusUseCase {

    private final ParadoxResolutionPhaseRepository phaseRepository;

    private final PlayerStateRepository playerStateRepository;

    private final ReactiveOfferRepository reactiveOfferRepository;

    private final Clock clock;

    GetParadoxResolutionStatusQueryHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            ReactiveOfferRepository reactiveOfferRepository,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.reactiveOfferRepository = reactiveOfferRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        var caller = requireParticipant(query);
        var phase = phaseRepository
                .findByGameIdAndEraNumber(query.gameId(), query.eraNumber())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotFoundException(query.gameId(), query.eraNumber()));
        var now = clock.instant();
        var phaseOpen = phase.status() == ParadoxResolutionPhaseStatus.OPEN && now.isBefore(phase.expiresAt());
        var timerRemainingSeconds = phaseOpen ? timerRemainingSeconds(phase.expiresAt()) : null;
        var submittedPlayerIds = phase.submittedPlayerIds();
        var allPlayerIds = playerStateRepository.findAllByGameId(query.gameId()).stream()
                .map(PlayerState::playerId)
                .distinct()
                .toList();
        var totalPlayers = Math.max(allPlayerIds.size(), submittedPlayerIds.size());
        List<UUID> pendingPlayerIds = null;
        if (phaseOpen) {
            pendingPlayerIds = allPlayerIds.stream()
                    .filter(playerId -> !submittedPlayerIds.contains(playerId))
                    .toList();
        }
        var mySubmitted = submittedPlayerIds.contains(query.callerPlayerId());
        return new Result(
                phase.eraNumber(),
                phaseOpen,
                timerRemainingSeconds,
                submittedPlayerIds.size(),
                totalPlayers,
                pendingPlayerIds,
                mySubmitted,
                phase.affectedEventIds().stream().toList(),
                phaseOpen && !mySubmitted ? eligibleResolutionCards(query, caller) : null);
    }

    private PlayerState requireParticipant(Query query) {
        // Same 404 as an unknown phase so outsiders cannot probe which games or eras exist.
        return playerStateRepository
                .findByGameIdAndPlayerId(query.gameId(), query.callerPlayerId())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotFoundException(query.gameId(), query.eraNumber()));
    }

    private List<EligibleCard> eligibleResolutionCards(Query query, PlayerState caller) {
        var cards = new ArrayList<EligibleCard>();
        caller.hand().stream()
                .filter(card -> ParadoxResolutionPhase.ELIGIBLE_CARD_TYPES.contains(card.cardType()))
                .map(card -> new EligibleCard(card.cardInstanceId(), card.cardType(), card.grade()))
                .forEach(cards::add);
        reactiveOfferRepository
                .findByGameIdAndEraNumberAndPlayerId(query.gameId(), query.eraNumber(), query.callerPlayerId())
                .filter(offer -> offer.status() == ReactiveOfferStatus.OFFERED)
                .ifPresent(offer -> offer.eligibleCards().stream()
                        .map(card -> new EligibleCard(card.cardInstanceId(), card.cardType(), CardGrade.I))
                        .forEach(cards::add));
        return List.copyOf(cards);
    }

    private int timerRemainingSeconds(java.time.Instant expiresAt) {
        var remainingSeconds = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.clamp(remainingSeconds, 0, Integer.MAX_VALUE);
    }
}
