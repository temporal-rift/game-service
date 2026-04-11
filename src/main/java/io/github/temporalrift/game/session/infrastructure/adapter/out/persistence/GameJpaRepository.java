package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface GameJpaRepository extends JpaRepository<GameJpaEntity, UUID> {}
