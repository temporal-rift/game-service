package io.github.temporalrift.game.action.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

/**
 * Confirms that a submitted {@code targetPlayerId} is a participant in the submitting player's game.
 * {@code PlayerStateRepository.findByGameIdAndPlayerId} is scoped to the pair, so it returns empty
 * identically whether the target doesn't exist at all or exists only in a different game — the
 * resulting {@link PlayerStateNotFoundException} never reveals which case occurred.
 */
@Component
public class GameParticipantValidator {

    private final PlayerStateRepository playerStateRepository;

    public GameParticipantValidator(PlayerStateRepository playerStateRepository) {
        this.playerStateRepository = playerStateRepository;
    }

    public void requireParticipant(UUID gameId, UUID targetPlayerId) {
        if (targetPlayerId == null) {
            return;
        }
        playerStateRepository
                .findByGameIdAndPlayerId(gameId, targetPlayerId)
                .orElseThrow(() -> new PlayerStateNotFoundException(gameId, targetPlayerId));
    }
}
