package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

public interface EndGameSaga {

    void start(UUID gameId, EndGameTrigger triggerType, UUID... playerIds);
}
