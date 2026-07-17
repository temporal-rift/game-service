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
     * Pessimistically locked variant for read-modify-write updates. Concurrent writers (e.g. the
     * faction and hand projection listeners firing after independent commits) must serialize on the
     * row or the last writer erases the other's field.
     */
    Optional<PlayerState> findByGameIdAndPlayerIdWithLock(UUID gameId, UUID playerId);

    List<PlayerState> findAllByGameId(UUID gameId);
}
