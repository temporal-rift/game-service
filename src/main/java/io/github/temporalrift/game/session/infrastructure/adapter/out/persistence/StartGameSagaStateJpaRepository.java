package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StartGameSagaStateJpaRepository extends JpaRepository<StartGameSagaStateJpaEntity, UUID> {

    Optional<StartGameSagaStateJpaEntity> findByGameId(UUID gameId);
}
