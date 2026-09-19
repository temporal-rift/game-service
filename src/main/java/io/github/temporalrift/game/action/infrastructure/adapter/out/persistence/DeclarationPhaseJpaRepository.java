package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DeclarationPhaseJpaRepository extends JpaRepository<DeclarationPhaseJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO declaration_phase
                (id, game_id, era_number, status, expires_at)
            VALUES (:id, :gameId, :eraNumber, :status, :expiresAt)
            ON CONFLICT (game_id, era_number) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("status") String status,
            @Param("expiresAt") Instant expiresAt);

    Optional<DeclarationPhaseJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select phase from DeclarationPhaseJpaEntity phase "
            + "where phase.gameId = :gameId and phase.eraNumber = :eraNumber")
    Optional<DeclarationPhaseJpaEntity> findByGameIdAndEraNumberWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select phase from DeclarationPhaseJpaEntity phase where phase.id = :id")
    Optional<DeclarationPhaseJpaEntity> findByIdWithLock(@Param("id") UUID id);

    @Query("select phase.id from DeclarationPhaseJpaEntity phase "
            + "where phase.status = 'OPEN' and phase.expiresAt <= :now")
    List<UUID> findOpenDueIds(@Param("now") Instant now);
}
