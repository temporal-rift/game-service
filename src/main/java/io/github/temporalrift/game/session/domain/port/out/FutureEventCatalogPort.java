package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;

public interface FutureEventCatalogPort {

    /**
     * Returns the IDs of all events in the catalog. Order is not guaranteed; callers are responsible for shuffling
     * before constructing a deck.
     */
    List<UUID> allEventIds();

    /**
     * Returns the {@link FutureEventDefinition} for each of the given IDs, in the same order as the input list.
     *
     * @throws IllegalStateException if any ID is not present in the catalog — this indicates a misconfigured catalog,
     *     not a recoverable domain failure
     */
    List<FutureEventDefinition> findByEventIds(List<UUID> eventIds);
}
