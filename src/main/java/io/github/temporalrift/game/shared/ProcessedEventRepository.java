package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Claims Kafka event IDs for a named consumer before business processing starts.
 *
 * <p>The claim and the consumer's business mutation must be committed in the same transaction. A
 * duplicate claim means the same consumer has already processed the event and must skip it.
 */
public interface ProcessedEventRepository {

    boolean tryMarkProcessed(UUID eventId, String consumer);
}
