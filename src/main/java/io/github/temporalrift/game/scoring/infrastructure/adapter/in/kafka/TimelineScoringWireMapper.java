package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import java.util.List;

import org.mapstruct.Mapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainBrokenPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OutcomeAppliedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxCascadedPayload;
import io.github.temporalrift.game.scoring.domain.event.ChainBroken;
import io.github.temporalrift.game.scoring.domain.event.ChainCompleted;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.event.ParadoxCascaded;

/** Maps the published {@code timeline.events} payloads onto the scoring module's own event records. */
@Mapper(componentModel = "spring")
interface TimelineScoringWireMapper {

    OutcomeApplied fromWire(OutcomeAppliedPayload payload);

    ChainCompleted fromWire(ChainCompletedPayload payload);

    ChainBroken fromWire(ChainBrokenPayload payload);

    EraResolutionCompleted fromWire(EraResolutionCompletedPayload payload);

    /**
     * {@code detonatedByPlayerIds} is optional in the contract, defaulting to an empty list. Normalizing it here
     * keeps the null off the non-null cascade-fact column and out of the era score evaluator, which reads the list
     * to decide the penalty multiplier.
     */
    default ParadoxCascaded fromWire(ParadoxCascadedPayload payload) {
        return new ParadoxCascaded(
                payload.gameId(),
                payload.eraNumber(),
                payload.paradoxId(),
                payload.affectedEventId(),
                payload.detonatedByPlayerIds() == null ? List.of() : List.copyOf(payload.detonatedByPlayerIds()));
    }
}
