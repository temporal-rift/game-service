package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.game.Game;

public interface GameRepository {

    Game save(Game game);

    Optional<Game> findById(UUID id);
}
