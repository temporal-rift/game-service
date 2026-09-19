package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReactiveOfferJpaRepository extends JpaRepository<ReactiveOfferJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO reactive_offer
                (id, game_id, era_number, player_id, stabilize_card_instance_id,
                 detonate_card_instance_id, consumed_card_instance_id, status)
            VALUES (:id, :gameId, :eraNumber, :playerId, :stabilizeCardInstanceId,
                    :detonateCardInstanceId, NULL, :status)
            ON CONFLICT (game_id, era_number, player_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("playerId") UUID playerId,
            @Param("stabilizeCardInstanceId") UUID stabilizeCardInstanceId,
            @Param("detonateCardInstanceId") UUID detonateCardInstanceId,
            @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from ReactiveOfferJpaEntity offer "
            + "where offer.gameId = :gameId and offer.eraNumber = :eraNumber and offer.playerId = :playerId")
    Optional<ReactiveOfferJpaEntity> findByGameIdAndEraNumberAndPlayerIdWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber, @Param("playerId") UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from ReactiveOfferJpaEntity offer "
            + "where offer.gameId = :gameId and offer.eraNumber = :eraNumber")
    List<ReactiveOfferJpaEntity> findAllByGameIdAndEraNumberWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);
}
