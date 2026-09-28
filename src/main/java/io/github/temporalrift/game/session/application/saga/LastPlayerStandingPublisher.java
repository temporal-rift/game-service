package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * Single publisher for the last-player-standing win so the abandonment path and the deferred era-end decision cannot
 * diverge on its shape.
 */
@Component
class LastPlayerStandingPublisher {

    static final String WIN_TYPE = "LAST_PLAYER_STANDING";

    private final SessionEventPublisher eventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final Clock clock;

    LastPlayerStandingPublisher(
            SessionEventPublisher eventPublisher, ApplicationEventPublisher applicationEventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.clock = clock;
    }

    void publish(Game game, LobbyPlayer winner, int finalScore) {
        var winConditionMet = new WinConditionMet(
                game.id(), winner.playerId(), winner.faction().name(), finalScore, WIN_TYPE);
        sagaHandoffPublisher.publish(
                eventPublisher::publish,
                DomainEventEnvelope.create(
                        game.id(),
                        Game.AGGREGATE_TYPE,
                        game.id(),
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        winConditionMet,
                        clock));
    }
}
