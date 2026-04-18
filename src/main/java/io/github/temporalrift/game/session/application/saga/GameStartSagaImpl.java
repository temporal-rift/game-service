package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.temporalrift.game.session.domain.lobby.Lobby;

@Service
class GameStartSagaImpl implements GameStartSaga {

    @Override
    public void start(UUID gameId, Lobby lobby) {}
}
