package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOfferStatus;

@Component
class ReactiveOfferRepositoryAdapter implements ReactiveOfferRepository {

    private final ReactiveOfferJpaRepository jpaRepository;

    ReactiveOfferRepositoryAdapter(ReactiveOfferJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReactiveOffer save(ReactiveOffer offer) {
        jpaRepository.save(toEntity(offer));
        return offer;
    }

    @Override
    public boolean createIfAbsent(ReactiveOffer offer) {
        if (offer.status() != ReactiveOfferStatus.OFFERED) {
            throw new IllegalArgumentException("Only offered reactive offers can be created");
        }
        return jpaRepository.insertIfAbsent(
                        offer.id(),
                        offer.gameId(),
                        offer.eraNumber(),
                        offer.playerId(),
                        offer.stabilizeCardInstanceId(),
                        offer.detonateCardInstanceId(),
                        offer.status().name())
                == 1;
    }

    @Override
    public Optional<ReactiveOffer> findByGameIdAndEraNumberAndPlayerIdWithLock(
            UUID gameId, int eraNumber, UUID playerId) {
        return jpaRepository
                .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, eraNumber, playerId)
                .map(this::toDomain);
    }

    @Override
    public Optional<ReactiveOffer> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId) {
        return jpaRepository
                .findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerId)
                .map(this::toDomain);
    }

    @Override
    public List<ReactiveOffer> findAllByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberWithLock(gameId, eraNumber).stream()
                .map(this::toDomain)
                .toList();
    }

    private ReactiveOfferJpaEntity toEntity(ReactiveOffer offer) {
        var entity = new ReactiveOfferJpaEntity();
        entity.setId(offer.id());
        entity.setGameId(offer.gameId());
        entity.setEraNumber(offer.eraNumber());
        entity.setPlayerId(offer.playerId());
        entity.setStabilizeCardInstanceId(offer.stabilizeCardInstanceId());
        entity.setDetonateCardInstanceId(offer.detonateCardInstanceId());
        entity.setConsumedCardInstanceId(offer.consumedCardInstanceId());
        entity.setStatus(offer.status().name());
        return entity;
    }

    private ReactiveOffer toDomain(ReactiveOfferJpaEntity entity) {
        return ReactiveOffer.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getPlayerId(),
                new ReactiveOffer.PersistedState(
                        entity.getStabilizeCardInstanceId(),
                        entity.getDetonateCardInstanceId(),
                        entity.getConsumedCardInstanceId(),
                        ReactiveOfferStatus.valueOf(entity.getStatus())));
    }
}
