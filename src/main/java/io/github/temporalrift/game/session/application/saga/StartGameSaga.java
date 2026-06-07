package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

public interface StartGameSaga {

    UUID start(UUID lobbyId, UUID requestingPlayerId);
}
