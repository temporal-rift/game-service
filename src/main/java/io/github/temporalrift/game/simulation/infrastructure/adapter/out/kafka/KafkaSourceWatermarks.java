package io.github.temporalrift.game.simulation.infrastructure.adapter.out.kafka;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;
import io.github.temporalrift.game.simulation.domain.port.out.SourceWatermarks;

/** Committed offsets of this service's consumer groups: how far each group has handled its source partitions. */
@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class KafkaSourceWatermarks implements SourceWatermarks {

    private static final String GROUP_PREFIX = "game-service";

    private final KafkaAdmin kafkaAdmin;

    KafkaSourceWatermarks(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public List<SourceWatermark> current() {
        try (var admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var watermarks = new ArrayList<SourceWatermark>();
            var groupIds = admin.listConsumerGroups().all().get().stream()
                    .map(ConsumerGroupListing::groupId)
                    .filter(groupId -> groupId.startsWith(GROUP_PREFIX))
                    .sorted()
                    .toList();
            for (var groupId : groupIds) {
                admin.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata()
                        .get()
                        .forEach((partition, offset) -> watermarks.add(new SourceWatermark(
                                groupId, partition.topic(), partition.partition(), offset.offset())));
            }
            watermarks.sort(Comparator.comparing(SourceWatermark::groupId)
                    .thenComparing(SourceWatermark::topic)
                    .thenComparingInt(SourceWatermark::partition));
            return List.copyOf(watermarks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading consumer group offsets", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not read consumer group offsets", e);
        }
    }
}
