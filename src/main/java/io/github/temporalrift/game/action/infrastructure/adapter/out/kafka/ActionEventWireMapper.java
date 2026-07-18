package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.game.action.ActionRoundClosed;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundClosedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundStartedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundTimerExpiredPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionSummary;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.CardPlayedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.PlayerSkippedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.RoundSummaryPublishedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.SpecialActionPlayedPayload;

@Mapper(componentModel = "spring")
interface ActionEventWireMapper {

    ActionRoundStartedPayload toWire(ActionRoundStarted event);

    CardPlayedPayload toWire(CardPlayed event);

    SpecialActionPlayedPayload toWire(SpecialActionPlayed event);

    ActionRoundTimerExpiredPayload toWire(ActionRoundTimerExpired event);

    PlayerSkippedPayload toWire(PlayerSkipped event);

    ActionRoundClosedPayload toWire(ActionRoundClosed event);

    RoundSummaryPublishedPayload toWire(RoundSummaryPublished event);

    ActionSummary toWire(RoundSummaryPublished.ActionSummary summary);

    // BandedProbabilityPublished is not wired here - its apis spec entry is pending publication
    // (apis PR #4); it still publishes via the legacy EventEnvelope path until that lands.
}
