package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.UuidOrder;

@Component
@Profile("in-memory")
class InMemorySessionExecutionProbe implements ExecutionProbe {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String GRACE_PERIOD = "GRACE_PERIOD";

    private final LobbyJpaRepository lobbies;
    private final GameJpaRepository games;
    private final PlayerReconnectSagaStateJpaRepository reconnectSagas;
    private final EraSagaScoresUpdatedInboxJpaRepository scoresInbox;

    InMemorySessionExecutionProbe(
            LobbyJpaRepository lobbies,
            GameJpaRepository games,
            PlayerReconnectSagaStateJpaRepository reconnectSagas,
            EraSagaScoresUpdatedInboxJpaRepository scoresInbox) {
        this.lobbies = lobbies;
        this.games = games;
        this.reconnectSagas = reconnectSagas;
        this.scoresInbox = scoresInbox;
    }

    @Override
    public Observation observe(Instant now) {
        var gameId = lobbies.findAll().stream()
                .min(Comparator.comparing(LobbyJpaEntity::getId, UuidOrder.POSTGRES))
                .map(LobbyJpaEntity::getGameId)
                .orElse(null);
        var ended = games.findAll().stream().anyMatch(game -> !IN_PROGRESS.equals(game.getStatus()));
        var gracePeriods = reconnectSagas.findAll().stream()
                .filter(saga -> GRACE_PERIOD.equals(saga.getStatus()))
                .map(PlayerReconnectSagaStateJpaEntity::getGraceExpiresAt)
                .toList();
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
