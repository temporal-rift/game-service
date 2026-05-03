package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

public interface PlayerStateRepository {

    PlayerState save(PlayerState playerState);

    Optional<PlayerState> findById(UUID id);

    Optional<PlayerState> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    List<PlayerState> findAllByGameId(UUID gameId);
}
