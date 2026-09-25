package io.github.temporalrift.game.scoring.application.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.shared.domain.event.GameStarted;

/**
 * Projects public session events into the scoring-owned read model backing the score REST API:
 * player display names from the game's starting roster. Idempotent upsert, so Modulith's at-least-once listener
 * retry needs no extra dedup.
 */
@Component
class ScoringReadProjectionEventListener {

    private final ScoringPlayerRepository playerRepository;

    ScoringReadProjectionEventListener(ScoringPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @ApplicationModuleListener
    void onGameStarted(GameStarted event) {
        event.players()
                .forEach(player ->
                        playerRepository.upsertPlayerName(event.gameId(), player.playerId(), player.playerName()));
    }
}
