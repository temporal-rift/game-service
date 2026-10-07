package io.github.temporalrift.game.simulation.domain.execution;

public record SourceWatermark(String groupId, String topic, int partition, long nextOffset) {}
