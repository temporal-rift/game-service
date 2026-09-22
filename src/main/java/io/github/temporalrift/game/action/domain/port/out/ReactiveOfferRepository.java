package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;

public interface ReactiveOfferRepository {

    ReactiveOffer save(ReactiveOffer offer);

    /**
     * Persists a newly dealt offer, returning {@code false} when an offer for the same game, era, and
     * player already exists (redelivered phase facts never double-deal).
     */
    boolean createIfAbsent(ReactiveOffer offer);

    Optional<ReactiveOffer> findByGameIdAndEraNumberAndPlayerIdWithLock(UUID gameId, int eraNumber, UUID playerId);

    Optional<ReactiveOffer> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);

    List<ReactiveOffer> findAllByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber);
}
