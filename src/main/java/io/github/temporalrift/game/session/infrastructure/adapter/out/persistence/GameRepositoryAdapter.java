package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;

@Component
class GameRepositoryAdapter implements GameRepository {

    private final GameJpaRepository jpaRepository;

    GameRepositoryAdapter(GameJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Game save(Game game) {
        jpaRepository.save(toEntity(game));
        return game;
    }

    @Override
    public Optional<Game> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private GameJpaEntity toEntity(Game game) {
        var entity = new GameJpaEntity();
        entity.setId(game.id());
        entity.setLobbyId(game.lobbyId());
        entity.setStatus(game.status().name());
        entity.setEraCounter(game.eraCounter());
        entity.setCascadedParadoxCounter(game.cascadedParadoxCounter());
        entity.setAvailableEventIds(new ArrayList<>(game.availableEventIds()));
        return entity;
    }

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
