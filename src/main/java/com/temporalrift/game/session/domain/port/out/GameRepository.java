package com.temporalrift.game.session.domain.port.out;

import com.temporalrift.game.session.domain.game.Game;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository {

    Game save(Game game);

    Optional<Game> findById(UUID id);
}

