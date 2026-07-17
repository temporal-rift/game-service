package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

public interface PlayerStateRepository {

    PlayerState save(PlayerState playerState);

    Optional<PlayerState> findById(UUID id);

    Optional<PlayerState> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    /**
     * Atomically creates the player state if absent, then returns it under a pessimistic write
     * lock. For read-modify-write updates from concurrent writers (e.g. the faction and hand
     * projection listeners firing after independent commits): the lock serializes updates on the
     * row, and the create step must be conflict-free because a lock on a nonexistent row protects
     * nothing.
     */
    PlayerState findOrCreateWithLock(UUID gameId, UUID playerId);

    List<PlayerState> findAllByGameId(UUID gameId);
}
