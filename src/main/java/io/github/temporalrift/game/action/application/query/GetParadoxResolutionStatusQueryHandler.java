package io.github.temporalrift.game.action.application.query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.port.in.GetParadoxResolutionStatusUseCase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotFoundException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Service
@ConditionalOnBean({ParadoxResolutionPhaseRepository.class, PlayerStateRepository.class})
class GetParadoxResolutionStatusQueryHandler implements GetParadoxResolutionStatusUseCase {

    private final ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;

    private final PlayerStateRepository playerStateRepository;

    private final Clock clock;

    GetParadoxResolutionStatusQueryHandler(
            ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository,
            PlayerStateRepository playerStateRepository,
            Clock clock) {
        this.paradoxResolutionPhaseRepository = paradoxResolutionPhaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        // Same 404 as a missing phase so outsiders cannot probe which games/eras exist.
        playerStateRepository
                .findByGameIdAndPlayerId(query.gameId(), query.callerPlayerId())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotFoundException(query.gameId(), query.eraNumber()));
        var phase = paradoxResolutionPhaseRepository
                .findByGameIdAndEraNumber(query.gameId(), query.eraNumber())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotFoundException(query.gameId(), query.eraNumber()));
        var open = phase.status() == ParadoxResolutionPhaseStatus.OPEN;
        var players = playerStateRepository.findAllByGameId(query.gameId());
        var submittedPlayerIds = phase.submittedPlayerIds();
        var pendingPlayerIds = open
                ? players.stream()
                        .map(PlayerState::playerId)
                        .filter(playerId -> !submittedPlayerIds.contains(playerId))
                        .toList()
                : List.<java.util.UUID>of();
        return new Result(
                phase.eraNumber(),
                open,
                open ? timerRemainingSeconds(phase.expiresAt()) : 0,
                submittedPlayerIds.size(),
                players.size(),
                pendingPlayerIds,
                submittedPlayerIds.contains(query.callerPlayerId()));
    }

    private int timerRemainingSeconds(Instant expiresAt) {
        var remainingSeconds = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.clamp(remainingSeconds, 0, Integer.MAX_VALUE);
    }
}
