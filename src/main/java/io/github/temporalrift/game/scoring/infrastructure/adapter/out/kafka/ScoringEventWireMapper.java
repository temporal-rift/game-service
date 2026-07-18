package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.game.scoring.ScoresUpdated;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka.model.ScoreUpdate;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka.model.ScoresUpdatedPayload;

@Mapper(componentModel = "spring")
interface ScoringEventWireMapper {

    ScoresUpdatedPayload toWire(ScoresUpdated event);

    ScoreUpdate toWire(ScoresUpdated.ScoreUpdate update);
}
