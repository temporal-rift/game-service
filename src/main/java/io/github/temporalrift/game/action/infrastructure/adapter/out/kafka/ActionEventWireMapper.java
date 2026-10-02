package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;

import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundClosedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundPassedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundStartedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundTimerExpiredPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionSummary;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActivistDeclarationRecordedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardPlayedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.DeclarationOptionsOfferedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.DeclarationWindowOpenedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.EligibleResolutionCard;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeBehaviorChangedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeInfluenceSignature;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeSignatureRevealedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.HandCardInterceptedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.InfluenceSignatureType;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.InfluenceTracedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.InterceptedHandCard;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ParadoxResolutionCardPlayedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ParadoxResolutionCardsOfferedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ParadoxResolutionPassedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.PlayerJammedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.PlayerSkippedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.RoundSummaryPublishedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.SpecialActionPlayedPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundPassed;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.DeclarationOptionsOffered;
import io.github.temporalrift.game.action.domain.event.DeclarationWindowOpened;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ExposeSignatureRevealed;
import io.github.temporalrift.game.action.domain.event.HandCardIntercepted;
import io.github.temporalrift.game.action.domain.event.InfluenceTraced;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionPassed;
import io.github.temporalrift.game.action.domain.event.PlayerJammed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.domain.event.ActionRoundClosed;

@Mapper(componentModel = "spring")
interface ActionEventWireMapper {

    DeclarationWindowOpenedPayload toWire(DeclarationWindowOpened event);

    DeclarationOptionsOfferedPayload toWire(DeclarationOptionsOffered event);

    ActivistDeclarationRecordedPayload toWire(ActivistDeclarationRecorded event);

    ExposeSignatureRevealedPayload toWire(ExposeSignatureRevealed event);

    ExposeInfluenceSignature toWire(
            io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature signature);

    default InfluenceSignatureType toWire(io.github.temporalrift.game.shared.domain.model.CardType type) {
        return switch (type) {
            case PUSH -> InfluenceSignatureType.PUSH;
            case SUPPRESS -> InfluenceSignatureType.SUPPRESS;
            case SWING -> InfluenceSignatureType.SWING;
            default -> throw new IllegalArgumentException("Unsupported Expose signature card type: " + type);
        };
    }

    ExposeBehaviorChangedPayload toWire(ExposeBehaviorChanged event);

    InfluenceTracedPayload toWire(InfluenceTraced event);

    HandCardInterceptedPayload toWire(HandCardIntercepted event);

    InterceptedHandCard toWire(HandCardIntercepted.RevealedCard card);

    ActionRoundStartedPayload toWire(ActionRoundStarted event);

    CardPlayedPayload toWire(CardPlayed event);

    ActionRoundPassedPayload toWire(ActionRoundPassed event);

    SpecialActionPlayedPayload toWire(SpecialActionPlayed event);

    ActionRoundTimerExpiredPayload toWire(ActionRoundTimerExpired event);

    PlayerSkippedPayload toWire(PlayerSkipped event);

    ParadoxResolutionCardPlayedPayload toWire(ParadoxResolutionCardPlayed event);

    ParadoxResolutionCardsOfferedPayload toWire(ParadoxResolutionCardsOffered event);

    EligibleResolutionCard toWire(ParadoxResolutionCardsOffered.EligibleCard card);

    ParadoxResolutionPassedPayload toWire(ParadoxResolutionPassed event);

    PlayerJammedPayload toWire(PlayerJammed event);

    ActionRoundClosedPayload toWire(ActionRoundClosed event);

    RoundSummaryPublishedPayload toWire(RoundSummaryPublished event);

    ActionSummary toWire(RoundSummaryPublished.ActionSummary summary);
}
