package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import java.util.HashMap;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.JsonKafkaHeaderMapper;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.GameScope;

/**
 * Drops {@code timeline.events} records of games outside this deployment's scope before any listener sees them. A
 * dropped record counts as consumed, so its offset still advances the consumer groups' positions.
 */
@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class TimelineGameFilter implements RecordFilterStrategy<Object, Object> {

    static final String TOPIC = "timeline.events";
    static final String GAME_ID_HEADER = "gameId";

    private final GameScope scope;
    // The mapper the listeners' message converter reads headers with, so the filter sees the value they would.
    private final JsonKafkaHeaderMapper headerMapper = new JsonKafkaHeaderMapper();

    TimelineGameFilter(GameScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean filter(ConsumerRecord<Object, Object> consumerRecord) {
        if (!TOPIC.equals(consumerRecord.topic())) {
            return false;
        }
        var headers = new HashMap<String, Object>();
        headerMapper.toHeaders(consumerRecord.headers(), headers);
        return !scope.admits(gameIdOf(headers.get(GAME_ID_HEADER)));
    }

    /** The game named by a {@code gameId} header, or {@code null} when it is absent or not an identifier. */
    static UUID gameIdOf(Object header) {
        try {
            return header == null ? null : UUID.fromString(header.toString());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
