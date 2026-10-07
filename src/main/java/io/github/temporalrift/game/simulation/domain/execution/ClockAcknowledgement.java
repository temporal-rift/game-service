package io.github.temporalrift.game.simulation.domain.execution;

import java.time.Instant;
import java.util.UUID;

public record ClockAcknowledgement(UUID operationId, long appliedRevision, Instant logicalTime) {}
