package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper;
import org.springframework.kafka.support.JsonKafkaHeaderMapper;
import org.springframework.messaging.MessageHeaders;

class TimelineGameFilterTest {

    private static final UUID GAME = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final TimelineGameFilter filter = new TimelineGameFilter(GAME::equals);

    /** A record whose headers a producer wrote with the given mapper, as the timeline service's records are. */
    private static ConsumerRecord<Object, Object> recordOf(
            String topic, String gameId, org.springframework.kafka.support.KafkaHeaderMapper producer) {
        var consumed = new ConsumerRecord<Object, Object>(topic, 0, 0L, "key", new byte[0]);
        var headers = new LinkedHashMap<String, Object>();
        if (gameId != null) {
            headers.put("gameId", gameId);
        }
        producer.fromHeaders(new MessageHeaders(headers), consumed.headers());
        return consumed;
    }

    private static ConsumerRecord<Object, Object> recordOf(String topic, String gameId) {
        return recordOf(topic, gameId, new BinderHeaderMapper());
    }

    @Test
    @DisplayName("a timeline record of the game in scope is kept, however its producer encoded the header")
    void filter_gameInScope_keepsTheRecord() {
        assertThat(filter.filter(recordOf("timeline.events", GAME.toString()))).isFalse();
        assertThat(filter.filter(recordOf("timeline.events", GAME.toString(), new JsonKafkaHeaderMapper())))
                .isFalse();
    }

    @Test
    @DisplayName("a timeline record of another game, of no game or of a malformed game is dropped")
    void filter_gameOutOfScope_dropsTheRecord() {
        assertThat(filter.filter(recordOf("timeline.events", UUID.randomUUID().toString())))
                .isTrue();
        assertThat(filter.filter(recordOf("timeline.events", null))).isTrue();
        assertThat(filter.filter(recordOf("timeline.events", "not-a-uuid"))).isTrue();
    }

    @Test
    @DisplayName("records of other topics are never dropped")
    void filter_otherTopic_keepsTheRecord() {
        assertThat(filter.filter(recordOf("game.commands", UUID.randomUUID().toString())))
                .isFalse();
    }
}
