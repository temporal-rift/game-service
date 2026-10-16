package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.UuidOrder;

@Component
@Profile("in-memory")
class InMemorySessionExecutionProbe implements ExecutionProbe {

    private static final String IN_PROGRESS = GameStatus.IN_PROGRESS.name();
    private static final String GRACE_PERIOD = PlayerReconnectSagaStatus.GRACE_PERIOD.name();

    private final InMemoryLobbyJpaRepository lobbies;
    private final InMemoryGameJpaRepository games;
    private final InMemoryPlayerReconnectSagaStateJpaRepository reconnectSagas;
    private final EraSagaScoresUpdatedInboxJpaRepository scoresInbox;

    InMemorySessionExecutionProbe(
            InMemoryLobbyJpaRepository lobbies,
            InMemoryGameJpaRepository games,
            InMemoryPlayerReconnectSagaStateJpaRepository reconnectSagas,
            EraSagaScoresUpdatedInboxJpaRepository scoresInbox) {
        this.lobbies = lobbies;
        this.games = games;
        this.reconnectSagas = reconnectSagas;
        this.scoresInbox = scoresInbox;
    }

    @Override
    public Observation observe(Instant now) {
        var gameId = lobbies.project(lobby -> true, lobby -> lobby).stream()
                .min(Comparator.comparing(LobbyJpaEntity::getId, UuidOrder.POSTGRES))
                .map(LobbyJpaEntity::getGameId)
                .orElse(null);
        var ended = !games.project(game -> !IN_PROGRESS.equals(game.getStatus()), GameJpaEntity::getId)
                .isEmpty();
        var gracePeriods = reconnectSagas.project(
                saga -> GRACE_PERIOD.equals(saga.getStatus()), PlayerReconnectSagaStateJpaEntity::getGraceExpiresAt);
        var due = (int)
                gracePeriods.stream().filter(expiry -> !expiry.isAfter(now)).count();
        var next = gracePeriods.stream()
                .filter(expiry -> expiry.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new Observation(
                gameId, ended, due, scoresInbox.findRecordedButNotAdvanced().size(), next);
    }
}
