package io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest;

import java.time.ZoneOffset;

import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.simulation.domain.execution.ClockAcknowledgement;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;
import io.github.temporalrift.game.simulation.domain.execution.EntropyVersion;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionCheckpoint;
import io.github.temporalrift.game.simulation.domain.execution.Seed;
import io.github.temporalrift.game.simulation.domain.execution.SimulationSeat;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionState;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.SourceWatermark;

final class SimulationControlMapper {

    private SimulationControlMapper() {}

    static io.github.temporalrift.game.simulation.domain.execution.ExecutionContext toDomain(
            io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionContext body) {
        return new io.github.temporalrift.game.simulation.domain.execution.ExecutionContext(
                body.getCaseKey(),
                new Seed(body.getSeed()),
                EntropyVersion.valueOf(body.getEntropyVersion().getValue()),
                body.getManifestDigest(),
                body.getLogicalTime().toInstant(),
                body.getSeats().stream()
                        .map(seat -> new SimulationSeat(
                                seat.getSeatIndex(),
                                seat.getPlayerId(),
                                Faction.valueOf(seat.getFaction().getValue())))
                        .toList());
    }

    static ClockAdvance toDomain(
            io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ClockAdvance body) {
        return new ClockAdvance(
                body.getOperationId(),
                body.getExpectedRevision(),
                body.getTargetTime().toInstant());
    }

    static io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionCheckpoint toModel(
            ExecutionCheckpoint checkpoint) {
        return new io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionCheckpoint(
                checkpoint.caseKey(),
                checkpoint.manifestDigest(),
                checkpoint.revision(),
                checkpoint.logicalTime().atOffset(ZoneOffset.UTC),
                checkpoint.gameId(),
                ExecutionState.fromValue(checkpoint.state().name()),
                checkpoint.drained(),
                checkpoint.outboxPending(),
                checkpoint.continuationsPending(),
                checkpoint.dueTimersPending(),
                checkpoint.nextDeadline() == null
                        ? null
                        : checkpoint.nextDeadline().atOffset(ZoneOffset.UTC),
                checkpoint.sourceWatermarks().stream()
                        .map(watermark -> new SourceWatermark(
                                watermark.groupId(), watermark.topic(), watermark.partition(), watermark.nextOffset()))
                        .toList());
    }

    static io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ClockAcknowledgement toModel(
            ClockAcknowledgement acknowledgement) {
        return new io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ClockAcknowledgement(
                acknowledgement.operationId(),
                acknowledgement.appliedRevision(),
                acknowledgement.logicalTime().atOffset(ZoneOffset.UTC));
    }
}
