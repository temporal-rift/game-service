package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundClosedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundStartedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundTimerExpiredPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionSummary;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActivistDeclarationRecordedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.BandedProbabilityEventBandState;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.BandedProbabilityOutcomeBandState;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.BandedProbabilityPublishedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardPlayedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeBehaviorChangedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeInfluenceSignature;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeSignatureRevealedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.InfluenceSignatureType;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.PlayerSkippedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.RoundSummaryPublishedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.SpecialActionPlayedPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ExposeSignatureRevealed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.ActionRoundClosed;

@Mapper(componentModel = "spring")
interface ActionEventWireMapper {

    ActivistDeclarationRecordedPayload toWire(ActivistDeclarationRecorded event);

    ExposeSignatureRevealedPayload toWire(ExposeSignatureRevealed event);

    ExposeInfluenceSignature toWire(
            io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature signature);

    default InfluenceSignatureType toWire(io.github.temporalrift.game.shared.CardType type) {
        return switch (type) {
            case PUSH -> InfluenceSignatureType.PUSH;
            case SUPPRESS -> InfluenceSignatureType.SUPPRESS;
            case SWING -> InfluenceSignatureType.SWING;
            default -> throw new IllegalArgumentException("Unsupported Expose signature card type: " + type);
        };
    }

    ExposeBehaviorChangedPayload toWire(ExposeBehaviorChanged event);

    ActionRoundStartedPayload toWire(ActionRoundStarted event);

    CardPlayedPayload toWire(CardPlayed event);

    SpecialActionPlayedPayload toWire(SpecialActionPlayed event);

    ActionRoundTimerExpiredPayload toWire(ActionRoundTimerExpired event);

    PlayerSkippedPayload toWire(PlayerSkipped event);

    ActionRoundClosedPayload toWire(ActionRoundClosed event);

    RoundSummaryPublishedPayload toWire(RoundSummaryPublished event);

    ActionSummary toWire(RoundSummaryPublished.ActionSummary summary);

    BandedProbabilityPublishedPayload toWire(BandedProbabilityPublished event);

    BandedProbabilityEventBandState toWire(BandedProbabilityPublished.EventBandState eventBandState);

    BandedProbabilityOutcomeBandState toWire(BandedProbabilityPublished.OutcomeBandState outcomeBandState);
}
