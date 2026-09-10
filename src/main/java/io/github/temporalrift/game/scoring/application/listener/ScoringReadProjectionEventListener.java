package io.github.temporalrift.game.scoring.application.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

/**
 * Projects public session events into the scoring-owned read model backing the score REST API:
 * player display names from lobby joins. Idempotent upsert, so Modulith's at-least-once listener
 * retry needs no extra dedup.
 *
 * <p>Faction visibility and the Revisionist unidentified-faction bonus are applied synchronously
 * from {@code EndGameSagaImpl} via {@code FactionRevealCommand} instead of an
 * {@code @ApplicationModuleListener} here — see that command's javadoc for why.
 */
@Component
class ScoringReadProjectionEventListener {

    private final ScoringPlayerRepository playerRepository;

    ScoringReadProjectionEventListener(ScoringPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @ApplicationModuleListener
    void onPlayerJoinedLobby(PlayerJoinedLobby event) {
        playerRepository.upsertPlayerName(event.gameId(), event.playerId(), event.playerName());
    }
}
