package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.game.Game;

public interface GameRepository {

    Game save(Game game);

    Optional<Game> findById(UUID id);

    /**
     * Pessimistically locked variant for decisions that must be serialized per game — e.g. the
     * "was this the last player?" finalization check after an abandonment, where two concurrent
     * transactions could otherwise each see the other's uncommitted state and both skip the
     * game-over publication.
     */
    Optional<Game> findByIdWithLock(UUID id);
}
