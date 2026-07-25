package io.github.temporalrift.game.scoring.application.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

/**
 * Projects public session events into the scoring-owned read model backing the score REST API:
 * player display names (from lobby joins) and faction visibility (flipped on at game end). Both
 * writes are idempotent upserts, so Modulith's at-least-once listener retry needs no extra dedup.
 */
@Component
class ScoringReadProjectionEventListener {

    private final ScoringGameVisibilityRepository visibilityRepository;
    private final ScoringPlayerRepository playerRepository;

    ScoringReadProjectionEventListener(
            ScoringGameVisibilityRepository visibilityRepository, ScoringPlayerRepository playerRepository) {
        this.visibilityRepository = visibilityRepository;
        this.playerRepository = playerRepository;
    }

    @ApplicationModuleListener
    void onPlayerJoinedLobby(PlayerJoinedLobby event) {
        playerRepository.upsertPlayerName(event.gameId(), event.playerId(), event.playerName());
    }

    @ApplicationModuleListener
    void onFactionRevealed(FactionRevealed event) {
        visibilityRepository.markFactionsRevealed(event.gameId());
    }
}
