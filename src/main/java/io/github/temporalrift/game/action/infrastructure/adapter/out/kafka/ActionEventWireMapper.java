package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundClosedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundStartedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundTimerExpiredPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionSummary;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActivistDeclarationRecordedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.BandedProbabilityEventBandState;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.BandedProbabilityOutcomeBandState;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.BandedProbabilityPublishedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.CardPlayedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ExposeBehaviorChangedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ExposeInfluenceSignature;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ExposeSignatureRevealedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.InfluenceSignatureType;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.PlayerSkippedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.RoundSummaryPublishedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.SpecialActionPlayedPayload;
import io.github.temporalrift.game.shared.ActionRoundClosed;

@Mapper(componentModel = "spring")
interface ActionEventWireMapper {

    @Mapping(
            target = "roundNumber",
            expression = "java(ActivistDeclarationRecordedPayload.RoundNumber.fromValue(event.roundNumber()))")
    ActivistDeclarationRecordedPayload toWire(ActivistDeclarationRecorded event);

    @Mapping(
            target = "roundNumber",
            expression = "java(ExposeSignatureRevealedPayload.RoundNumber.fromValue(event.roundNumber()))")
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

    @Mapping(
            target = "roundNumber",
            expression = "java(ExposeBehaviorChangedPayload.RoundNumber.fromValue(event.roundNumber()))")
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
