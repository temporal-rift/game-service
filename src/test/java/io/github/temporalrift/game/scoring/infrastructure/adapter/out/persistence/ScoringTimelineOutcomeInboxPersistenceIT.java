package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

@PersistenceIntegrationTest
class ScoringTimelineOutcomeInboxPersistenceIT {

    @Autowired
    TimelineOutcomeInboxRepository outcomeInboxRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void save_duplicateWithinSameTransactionDoesNotPoisonSubsequentWrites() {
        // proves ON CONFLICT DO NOTHING, not check-then-insert or catch-and-swallow: a real
        // constraint violation inside a transaction (without a savepoint) would abort every
        // later statement in that same transaction, even if the Java exception were caught
        var outcome =
                new OutcomeApplied(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());
        var otherOutcome = new OutcomeApplied(
                outcome.gameId(), outcome.eraNumber(), UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());

        transactionTemplate.executeWithoutResult(_ -> {
            outcomeInboxRepository.save(outcome);
            outcomeInboxRepository.save(outcome); // exact duplicate — same (gameId, eraNumber, eventId)
            outcomeInboxRepository.save(otherOutcome); // must still succeed in the same transaction
        });

        var stored = outcomeInboxRepository.findByGameIdAndEraNumber(outcome.gameId(), outcome.eraNumber());
        assertThat(stored).containsExactlyInAnyOrder(outcome, otherOutcome);
    }

    @Test
    void save_keepsListedEmptyAndAbsentConsequentialErasuresDistinct() {
        var gameId = UUID.randomUUID();
        var erasedOutcomeId = UUID.randomUUID();
        var listed = new OutcomeApplied(
                gameId, 1, UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(erasedOutcomeId));
        var empty = new OutcomeApplied(gameId, 1, UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());
        var absent = new OutcomeApplied(gameId, 1, UUID.randomUUID(), UUID.randomUUID(), List.of(), null);

        transactionTemplate.executeWithoutResult(_ -> {
            outcomeInboxRepository.save(listed);
            outcomeInboxRepository.save(empty);
            outcomeInboxRepository.save(absent);
        });

        assertThat(outcomeInboxRepository.findByGameIdAndEraNumber(gameId, 1))
                .containsExactlyInAnyOrder(listed, empty, absent);
    }

    @Test
    void findByGameIdAndEraNumber_rowStoredBeforeTheListLoadsWithItAbsent() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        var legacyPayload = """
                {"gameId":"%s","eraNumber":1,"eventId":"%s","winningOutcomeId":"%s","finalProbabilities":[]}
                """.formatted(gameId, eventId, winningOutcomeId);
        jdbcTemplate.update("""
                INSERT INTO scoring_timeline_outcome_inbox
                    (id, game_id, era_number, event_id, winning_outcome_id, payload)
                VALUES (?, ?, 1, ?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), gameId, eventId, winningOutcomeId, legacyPayload);

        assertThat(outcomeInboxRepository.findByGameIdAndEraNumber(gameId, 1))
                .singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.eventId()).isEqualTo(eventId);
                    assertThat(outcome.consequentialErasures()).isNull();
                });
    }
}
