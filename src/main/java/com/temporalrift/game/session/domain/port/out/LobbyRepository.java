package com.temporalrift.game.session.domain.port.out;

import com.temporalrift.game.session.domain.lobby.Lobby;

import java.util.Optional;
import java.util.UUID;

public interface LobbyRepository {

    Lobby save(Lobby lobby);

    Optional<Lobby> findById(UUID id);

    Optional<Lobby> findByJoinCode(String joinCode);
}

