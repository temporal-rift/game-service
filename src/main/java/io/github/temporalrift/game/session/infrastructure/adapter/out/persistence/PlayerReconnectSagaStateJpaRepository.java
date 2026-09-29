package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerReconnectSagaStateJpaRepository extends JpaRepository<PlayerReconnectSagaStateJpaEntity, UUID> {

    Optional<PlayerReconnectSagaStateJpaEntity> findByGameIdAndPlayerIdAndStatus(
            UUID gameId, UUID playerId, String status);

    List<PlayerReconnectSagaStateJpaEntity> findAllByStatusAndGraceExpiresAtLessThanEqualOrderByGraceExpiresAt(
            String status, Instant deadline, Limit limit);

    List<PlayerReconnectSagaStateJpaEntity> findAllByGameIdAndStatusAndGraceExpiresAtLessThanEqual(
            UUID gameId, String status, Instant decisionAt);

    @Modifying
    @Query("UPDATE PlayerReconnectSagaStateJpaEntity s SET s.status = :next "
            + "WHERE s.sagaId = :sagaId AND s.status = :expected")
    int compareAndSetStatus(
            @Param("sagaId") UUID sagaId, @Param("expected") String expected, @Param("next") String next);
}
