package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.asyncapi.scoringevents.GeneratedChannelContract.ScoreUpdate;
import io.github.temporalrift.asyncapi.scoringevents.GeneratedChannelContract.ScoresUpdatedPayload;
import io.github.temporalrift.game.shared.ScoresUpdated;

@Mapper(componentModel = "spring")
interface ScoringEventWireMapper {

    ScoresUpdatedPayload toWire(ScoresUpdated event);

    ScoreUpdate toWire(ScoresUpdated.ScoreUpdate update);
}
