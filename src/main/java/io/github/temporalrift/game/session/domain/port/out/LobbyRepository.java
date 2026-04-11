package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.lobby.Lobby;

public interface LobbyRepository {

    Lobby save(Lobby lobby);

    Optional<Lobby> findById(UUID id);

    Optional<Lobby> findByJoinCode(String joinCode);
}
