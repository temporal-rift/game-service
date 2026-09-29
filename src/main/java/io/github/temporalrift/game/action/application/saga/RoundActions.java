package io.github.temporalrift.game.action.application.saga;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundCancellation;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

final class RoundActions {

    private RoundActions() {}

    /** The round's submissions whose player was not cancelled by a Nullify. */
    static List<SubmittedAction> live(ActionRound round) {
        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(round.submittedActions());
        return round.submittedActions().stream()
                .filter(action -> !cancelledPlayerIds.contains(action.playerId()))
                .toList();
    }

    static Set<UUID> obscureSubmitters(List<SubmittedAction> liveActions) {
        return liveActions.stream()
                .filter(SubmittedAction.SpecialActionSubmission.class::isInstance)
                .map(SubmittedAction.SpecialActionSubmission.class::cast)
                .filter(special -> special.specialAction() == SpecialAction.OBSCURE)
                .map(SubmittedAction.SpecialActionSubmission::playerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
