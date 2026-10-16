package io.github.temporalrift.game.simulation.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.SimulationEventDelivery;
import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;

class InMemorySourceWatermarksTest {

    @Test
    void reportsTheDeliveryPositionsAsWatermarks() {
        var delivery = mock(SimulationEventDelivery.class);
        given(delivery.positions())
                .willReturn(List.of(new SimulationEventDelivery.ConsumerPosition("group", "timeline.events", 0, 3L)));

        assertThat(new InMemorySourceWatermarks(delivery).current())
                .containsExactly(new SourceWatermark("group", "timeline.events", 0, 3L));
    }
}
