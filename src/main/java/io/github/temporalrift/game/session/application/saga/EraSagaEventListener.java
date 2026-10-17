package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ActionRoundClosed;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;

@Component
class EraSagaEventListener {

    private final EraSaga eraSaga;
    private final EraSagaAdvancer eraSagaAdvancer;
    private final EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;

    EraSagaEventListener(
            EraSaga eraSaga, EraSagaAdvancer eraSagaAdvancer, EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox) {
        this.eraSaga = eraSaga;
        this.eraSagaAdvancer = eraSagaAdvancer;
        this.scoresUpdatedInbox = scoresUpdatedInbox;
    }

    @ApplicationModuleListener
    void onEraStarted(EraStarted event) {
        eraSaga.start(event.gameId(), event.eraNumber(), event.playerIds(), event.carryOverEvents());
    }

    @ApplicationModuleListener
    void onActionRoundClosed(ActionRoundClosed event) {
        eraSagaAdvancer.handleRoundClosed(event.gameId(), event);
    }

    @ApplicationModuleListener
    void onScoresUpdated(ScoresUpdated event) {
        eraSagaAdvancer.handleScoresUpdated(event.gameId(), event);
    }

    @ApplicationModuleListener
    void onResolutionApplied(ResolutionAppliedApplicationEvent event) {
        scoresUpdatedInbox.findRecordedButNotAdvanced().stream()
                .filter(scores -> scores.gameId().equals(event.gameId()) && scores.eraNumber() == event.eraNumber())
                .forEach(scores -> eraSagaAdvancer.handleScoresUpdated(scores.gameId(), scores));
    }

    @ApplicationModuleListener
    void onBandsPublished(BandsPublishedApplicationEvent event) {
        eraSagaAdvancer.handleBandsPublished(event.gameId(), event.eraNumber());
    }

    @ApplicationModuleListener
    void onResolutionFailed(ResolutionFailedApplicationEvent event) {
        eraSagaAdvancer.handleResolutionFailed(event.gameId(), event.eraNumber());
    }
}
