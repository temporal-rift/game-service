package com.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.temporalrift.game.session.domain.game.Game;
import com.temporalrift.game.session.domain.game.GameStatus;
import com.temporalrift.game.session.domain.port.out.GameRepository;

@Component
class GameRepositoryAdapter implements GameRepository {

    private final GameJpaRepository jpaRepository;
    private final GamePersistenceMapper mapper;

    GameRepositoryAdapter(GameJpaRepository jpaRepository, GamePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Game save(Game game) {
        jpaRepository.save(mapper.toEntity(game));
        return game;
    }

    @Override
    public Optional<Game> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    //noinspection ClassReferencedRepeatedly
    private Game toDomain(GameJpaEntity entity) {
        return Game.reconstitute(
                entity.getId(),
                entity.getLobbyId(),
                new ArrayList<>(entity.getAvailableEventIds()),
                entity.getEraCounter(),
                entity.getCascadedParadoxCounter(),
                GameStatus.valueOf(entity.getStatus()));
    }
}
