package io.github.temporalrift.game.action.application.query;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;

@Service
@ConditionalOnBean({ActionRoundRepository.class, ActionRoundSagaRepository.class})
class GetRoundStatusQueryHandler implements GetRoundStatusUseCase {

    private final ActionRoundRepository actionRoundRepository;

    private final ActionRoundSagaRepository actionRoundSagaRepository;

    private final Clock clock;

    GetRoundStatusQueryHandler(
            ActionRoundRepository actionRoundRepository,
            ActionRoundSagaRepository actionRoundSagaRepository,
            Clock clock) {
        this.actionRoundRepository = actionRoundRepository;
        this.actionRoundSagaRepository = actionRoundSagaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        var round = actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(query.gameId(), query.eraNumber(), query.roundNumber())
                .orElseThrow(() -> new RoundNotFoundException(query.gameId(), query.eraNumber(), query.roundNumber()));
        var sagaState = actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(
                query.gameId(), query.eraNumber(), query.roundNumber());
        var timerRemainingSeconds = sagaState.map(this::timerRemainingSeconds).orElse(0);
        var pendingPlayerIds =
                sagaState.map(ActionRoundSagaState::pendingPlayerIds).orElseGet(round::pendingPlayerIds);
        var submittedCount = round.submittedActions().size();

        return new Result(
                round.eraNumber(),
                round.roundNumber(),
                restStatus(round.status()),
                timerRemainingSeconds,
                submittedCount,
                pendingPlayerIds.size() + submittedCount,
                pendingPlayerIds);
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
}
