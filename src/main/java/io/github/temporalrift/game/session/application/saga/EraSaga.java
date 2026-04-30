package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

interface EraSaga {

    void start(UUID gameId, int eraNumber, List<UUID> playerIds, List<UUID> cascadedEventIds);
}
