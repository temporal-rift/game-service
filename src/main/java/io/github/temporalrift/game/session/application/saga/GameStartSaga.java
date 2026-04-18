package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

import io.github.temporalrift.game.session.domain.lobby.Lobby;

public interface GameStartSaga {

    void start(UUID gameId, Lobby lobby);
}
