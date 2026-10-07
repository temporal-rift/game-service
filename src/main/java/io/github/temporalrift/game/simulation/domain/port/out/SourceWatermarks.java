package io.github.temporalrift.game.simulation.domain.port.out;

import java.util.List;

import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;

public interface SourceWatermarks {

    List<SourceWatermark> current();
}
