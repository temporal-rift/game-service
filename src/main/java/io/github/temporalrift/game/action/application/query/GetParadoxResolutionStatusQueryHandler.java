package io.github.temporalrift.game.action.application.query;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

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

    private final ParadoxResolutionPhaseRepository phaseRepository;

    private final PlayerStateRepository playerStateRepository;

    private final Clock clock;

    GetParadoxResolutionStatusQueryHandler(
            ParadoxResolutionPhaseRepository phaseRepository,
            PlayerStateRepository playerStateRepository,
            Clock clock) {
        this.phaseRepository = phaseRepository;
        this.playerStateRepository = playerStateRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        requireParticipant(query);
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
        return new Result(
                phase.eraNumber(),
                phaseOpen,
                timerRemainingSeconds,
                submittedPlayerIds.size(),
                totalPlayers,
                pendingPlayerIds,
                submittedPlayerIds.contains(query.callerPlayerId()));
    }

    private void requireParticipant(Query query) {
        // Same 404 as an unknown phase so outsiders cannot probe which games or eras exist.
        playerStateRepository
                .findByGameIdAndPlayerId(query.gameId(), query.callerPlayerId())
                .orElseThrow(() -> new ParadoxResolutionPhaseNotFoundException(query.gameId(), query.eraNumber()));
    }

    private int timerRemainingSeconds(java.time.Instant expiresAt) {
        var remainingSeconds = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.clamp(remainingSeconds, 0, Integer.MAX_VALUE);
    }
}
