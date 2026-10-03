package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;

@PersistenceIntegrationTest
class DeclarationPhaseRepositoryAdapterIT {
    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @Autowired
    DeclarationPhaseRepository repository;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void roundTripsDecisionsAndDuplicateOpeningPreservesThem() {
        var game = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var now = Instant.parse("2026-09-01T12:00:00Z");
        var phase = new DeclarationPhase(UUID.randomUUID(), game, 1, now.plusSeconds(120), List.of(first, second));
        transactions.executeWithoutResult(_ -> {
            assertThat(repository.createIfAbsent(phase)).isTrue();
            phase.decide(first, DeclarationDecision.DECLINED, now);
            repository.save(phase);
            entityManager.flush();
            entityManager.clear();
        });
        transactions.executeWithoutResult(_ -> {
            var loaded = repository.findByGameIdAndEraNumberWithLock(game, 1).orElseThrow();
            assertThat(loaded.decisions())
                    .containsEntry(first, DeclarationDecision.DECLINED)
                    .containsEntry(second, DeclarationDecision.PENDING);
            assertThat(repository.createIfAbsent(new DeclarationPhase(
                            UUID.randomUUID(), game, 1, now.plusSeconds(120), List.of(first, second))))
                    .isFalse();
            assertThat(loaded.decide(second, DeclarationDecision.DECLARED, now)).isTrue();
            repository.save(loaded);
            entityManager.flush();
            entityManager.clear();
        });
        transactions.executeWithoutResult(_ -> {
            var loaded = repository.findByGameIdAndEraNumberWithLock(game, 1).orElseThrow();
            assertThat(loaded.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
            assertThat(loaded.hasDeclined(first)).isTrue();
            assertThat(loaded.decisions().get(second)).isEqualTo(DeclarationDecision.DECLARED);
        });
    }
}
