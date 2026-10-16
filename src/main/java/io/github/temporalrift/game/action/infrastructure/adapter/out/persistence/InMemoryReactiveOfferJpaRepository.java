package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryReactiveOfferJpaRepository extends InMemoryJpaRepository<ReactiveOfferJpaEntity, UUID>
        implements ReactiveOfferJpaRepository {

    InMemoryReactiveOfferJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ReactiveOfferJpaEntity>of("reactive_offer", ReactiveOfferJpaEntity::getId)
                        .unique(
                                "game_era_player",
                                offer -> List.of(offer.getGameId(), offer.getEraNumber(), offer.getPlayerId())));
    }

    @Override
    public int insertIfAbsent(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID stabilizeCardInstanceId,
            UUID detonateCardInstanceId,
            String status) {
        var offer = new ReactiveOfferJpaEntity();
        offer.setId(id);
        offer.setGameId(gameId);
        offer.setEraNumber(eraNumber);
        offer.setPlayerId(playerId);
        offer.setStabilizeCardInstanceId(stabilizeCardInstanceId);
        offer.setDetonateCardInstanceId(detonateCardInstanceId);
        offer.setStatus(status);
        return insertRowIfAbsent(offer) ? 1 : 0;
    }

    @Override
    public Optional<ReactiveOfferJpaEntity> findByGameIdAndEraNumberAndPlayerIdWithLock(
            UUID gameId, int eraNumber, UUID playerId) {
        return findFirstForUpdate(offer -> matches(offer, gameId, eraNumber, playerId));
    }

    @Override
    public Optional<ReactiveOfferJpaEntity> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId) {
        return findFirst(offer -> matches(offer, gameId, eraNumber, playerId));
    }

    @Override
    public List<ReactiveOfferJpaEntity> findAllByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return findMatchingForUpdate(
                offer -> offer.getGameId().equals(gameId) && offer.getEraNumber() == eraNumber,
                Comparator.comparing(offer -> offer.getId().toString()));
    }

    private static boolean matches(ReactiveOfferJpaEntity offer, UUID gameId, int eraNumber, UUID playerId) {
        return offer.getGameId().equals(gameId)
                && offer.getEraNumber() == eraNumber
                && offer.getPlayerId().equals(playerId);
    }
}
