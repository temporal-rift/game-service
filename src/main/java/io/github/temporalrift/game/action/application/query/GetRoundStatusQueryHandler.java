package io.github.temporalrift.game.action.application.query;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;

@Service
@ConditionalOnBean({ActionRoundRepository.class, ActionRoundSagaRepository.class, PlayerStateRepository.class})
class GetRoundStatusQueryHandler implements GetRoundStatusUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final ActionRoundSagaRepository actionRoundSagaRepository;

    private final PlayerStateRepository playerStateRepository;

    private final Clock clock;

    GetRoundStatusQueryHandler(
            ActionRoundRepository actionRoundRepository,
            ActionRoundSagaRepository actionRoundSagaRepository,
            PlayerStateRepository playerStateRepository,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.actionRoundSagaRepository = actionRoundSagaRepository;
        this.playerStateRepository = playerStateRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        requireParticipant(query);
        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(query.gameId(), query.eraNumber(), query.roundNumber())
                .orElseThrow(() -> new RoundNotFoundException(query.gameId(), query.eraNumber(), query.roundNumber()));
        var sagaState = actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(
                query.gameId(), query.eraNumber(), query.roundNumber());
        var timerRemainingSeconds = sagaState.map(this::timerRemainingSeconds).orElse(0);
        var pendingPlayerIds =
                sagaState.map(ActionRoundSagaState::pendingPlayerIds).orElseGet(round::pendingPlayerIds);
        var submittedCount = round.submittedActions().size();
        var mySubmission = round.submittedActions().stream()
                .filter(action -> action.playerId().equals(query.callerPlayerId()))
                .findFirst()
                .map(action -> new MySubmission(true, actionType(action)))
                .orElseGet(() -> new MySubmission(false, null));

        return new Result(
                round.eraNumber(),
                round.roundNumber(),
                restStatus(round.status()),
                timerRemainingSeconds,
                submittedCount,
                pendingPlayerIds.size() + submittedCount,
                pendingPlayerIds,
                mySubmission);
    }

    private void requireParticipant(Query query) {
        // Same 404 as an unknown round so outsiders cannot probe which games or rounds exist.
        playerStateRepository
                .findByGameIdAndPlayerId(query.gameId(), query.callerPlayerId())
                .orElseThrow(() -> new RoundNotFoundException(query.gameId(), query.eraNumber(), query.roundNumber()));
    }

    private int timerRemainingSeconds(ActionRoundSagaState state) {
        var remainingSeconds =
                Duration.between(clock.instant(), state.timerExpiresAt()).toSeconds();
        return Math.clamp(remainingSeconds, 0, Integer.MAX_VALUE);
    }

    private String restStatus(RoundStatus status) {
        return switch (status) {
            case OPEN -> "OPEN";
            case CLOSING, CLOSED -> "CLOSED";
        };
    }

    private String actionType(SubmittedAction action) {
        return switch (action) {
            case SubmittedAction.CardAction ignored -> "CARD";
            case SubmittedAction.SpecialActionSubmission ignored -> "SPECIAL";
        };
    }
}
