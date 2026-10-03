package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.model.Faction;

@PersistenceIntegrationTest
class DeclineDeclarationConcurrencyIT {
    @Autowired
    PlayerStateRepository players;

    @Autowired
    DeclarationPhaseRepository phases;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void racingDuplicateOrExpiry_closesExactlyOnce(boolean raceExpiry) throws Exception {
        var now = Instant.parse("2026-09-01T12:00:00Z");
        var game = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var phase = new DeclarationPhase(UUID.randomUUID(), game, 1, now.plusSeconds(120), List.of(playerId));
        var player = new PlayerState(UUID.randomUUID(), game, playerId);
        player.assignFaction(Faction.ACTIVISTS);
        transactions.executeWithoutResult(_ -> {
            players.save(player);
            phases.createIfAbsent(phase);
        });
        var events = new ConcurrentLinkedQueue<Object>();
        var handler =
                new DeclineDeclarationCommandHandler(players, phases, events::add, Clock.fixed(now, ZoneOffset.UTC));
        var start = new CountDownLatch(1);
        var command = new DeclineDeclarationUseCase.Command(game, 1, playerId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var decline = executor.submit(() -> {
                start.await();
                try {
                    return transactions.execute(_ -> handler.handle(command));
                } catch (DeclarationWindowClosedException _) {
                    return null;
                }
            });
            var competing = executor.submit(() -> {
                start.await();
                transactions.executeWithoutResult(_ -> {
                    if (!raceExpiry) {
                        handler.handle(command);
                        return;
                    }
                    var locked = phases.findByIdWithLock(phase.id()).orElseThrow();
                    if (locked.closeIfOpen(now.plusSeconds(120))) {
                        phases.save(locked);
                        events.add(new DeclarationPhaseClosed(game, 1));
                    }
                });
                return null;
            });
            start.countDown();
            var result = decline.get(10, TimeUnit.SECONDS);
            competing.get(10, TimeUnit.SECONDS);
            assertThat(events).containsExactly(new DeclarationPhaseClosed(game, 1));
            transactions.executeWithoutResult(_ -> {
                var loaded = phases.findByIdWithLock(phase.id()).orElseThrow();
                assertThat(loaded.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
                assertThat(loaded.hasDeclined(playerId)).isEqualTo(result != null);
                if (result != null) {
                    assertThat(handler.handle(command)).isEqualTo(result);
                }
            });
            assertThat(events).hasSize(1);
        } finally {
            transactions.executeWithoutResult(_ -> {
                entityManager
                        .createNativeQuery("delete from declaration_phase where game_id = :game")
                        .setParameter("game", game)
                        .executeUpdate();
                entityManager
                        .createNativeQuery("delete from player_state where game_id = :game")
                        .setParameter("game", game)
                        .executeUpdate();
            });
        }
    }
}
