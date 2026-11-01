package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.lobby.Lobby;

/**
 * Persists lobbies. Every read returns the lobby's players in the order they joined, because that order fixes the
 * seats the game assigns when it starts.
 */
public interface LobbyRepository {

    Lobby save(Lobby lobby);

    Optional<Lobby> findById(UUID id);

    Optional<Lobby> findByIdWithLock(UUID id);

    Optional<Lobby> findByJoinCode(String joinCode);
}
