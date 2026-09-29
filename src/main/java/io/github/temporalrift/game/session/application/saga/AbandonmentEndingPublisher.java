package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.application.SagaHandoffPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * Publishes the same abandonment ending from immediate expiry and deferred era scoring.
 */
@Component
public class AbandonmentEndingPublisher {

    static final String WIN_TYPE = "LAST_PLAYER_STANDING";

    private final SessionEventPublisher eventPublisher;
    private final SagaHandoffPublisher sagaHandoffPublisher;
    private final Clock clock;

    AbandonmentEndingPublisher(
            SessionEventPublisher eventPublisher, ApplicationEventPublisher applicationEventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
        this.clock = clock;
    }

    public void publishAllPlayersAbandoned(Game game) {
        sagaHandoffPublisher.publish(
                eventPublisher::publish,
                DomainEventEnvelope.create(
                        game.id(),
                        Game.AGGREGATE_TYPE,
                        game.id(),
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        new GameEndedAbnormally(game.id(), GameEndedAbnormally.Reason.ALL_PLAYERS_ABANDONED),
                        clock));
    }

    void publishLastPlayerStanding(Game game, LobbyPlayer winner, int finalScore) {
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
