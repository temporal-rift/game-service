package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.game.session.domain.event.EraResolutionCompleted;

/** Maps the published {@code timeline.events} payloads onto the session module's own event records. */
@Mapper(componentModel = "spring")
interface TimelineSessionWireMapper {

    EraResolutionCompleted fromWire(EraResolutionCompletedPayload payload);
}
