package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostgresTestcontainersConfiguration.class,
    JacksonAutoConfiguration.class,
    TimelineOutcomeInboxRepositoryAdapter.class
})
class ScoringTimelineOutcomeInboxPersistenceIT {

    @Autowired
    TimelineOutcomeInboxRepository outcomeInboxRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void save_duplicateWithinSameTransactionDoesNotPoisonSubsequentWrites() {
        // proves ON CONFLICT DO NOTHING, not check-then-insert or catch-and-swallow: a real
        // constraint violation inside a transaction (without a savepoint) would abort every
        // later statement in that same transaction, even if the Java exception were caught
        var outcome = new OutcomeApplied(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of());
        var otherOutcome = new OutcomeApplied(
                outcome.gameId(), outcome.eraNumber(), UUID.randomUUID(), UUID.randomUUID(), List.of());

        transactionTemplate.executeWithoutResult(_ -> {
            outcomeInboxRepository.save(outcome);
            outcomeInboxRepository.save(outcome); // exact duplicate — same (gameId, eraNumber, eventId)
            outcomeInboxRepository.save(otherOutcome); // must still succeed in the same transaction
        });

        var stored = outcomeInboxRepository.findByGameIdAndEraNumber(outcome.gameId(), outcome.eraNumber());
        assertThat(stored).containsExactlyInAnyOrder(outcome, otherOutcome);
    }
}
