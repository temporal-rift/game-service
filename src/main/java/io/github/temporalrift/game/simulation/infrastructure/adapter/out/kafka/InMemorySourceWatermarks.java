package io.github.temporalrift.game.simulation.infrastructure.adapter.out.kafka;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.SimulationEventDelivery;
import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;
import io.github.temporalrift.game.simulation.domain.port.out.SourceWatermarks;

/** How far each consumer group has handled the records delivered to it, the in-memory twin of committed offsets. */
@Component
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemorySourceWatermarks implements SourceWatermarks {

    private final SimulationEventDelivery delivery;

    InMemorySourceWatermarks(SimulationEventDelivery delivery) {
        this.delivery = delivery;
    }

    @Override
    public List<SourceWatermark> current() {
        return delivery.positions().stream()
                .map(position -> new SourceWatermark(
                        position.groupId(), position.topic(), position.partition(), position.nextOffset()))
                .toList();
    }
}
