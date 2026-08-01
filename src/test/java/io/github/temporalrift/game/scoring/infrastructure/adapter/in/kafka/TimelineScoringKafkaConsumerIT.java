package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.SpecialAction;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TimelineScoringKafkaConsumerIT {

    @Autowired
    TimelineScoringKafkaConsumer consumer;

    @Autowired
    EraScoringContextRepository contextRepository;

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void handle_lastOutcomeAppliedForEra_updatesPlayerScoreAndPublishesScoresUpdated() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var eraNumber = 1;

        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
        contextRepository.recordChainFact(gameId, playerId, chainId, ScoreReason.CHAIN_LINK_ADDED, eraNumber);
        contextRepository.markActionFactsReady(gameId, eraNumber);

        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();

        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        var scores = playerScoreRepository.findAllByGameId(gameId);
        assertThat(scores).singleElement().satisfies(score -> {
            assertThat(score.playerId()).isEqualTo(playerId);
            assertThat(score.totalScore()).isEqualTo(ScoreReason.CHAIN_LINK_ADDED.pointsDelta());
            assertThat(score.history()).singleElement().satisfies(entry -> {
                assertThat(entry.reason()).isEqualTo(ScoreReason.CHAIN_LINK_ADDED);
                assertThat(entry.eraNumber()).isEqualTo(eraNumber);
            });
        });

        assertThat(scoresUpdatedOutboxRows()).isPositive();
    }

    @Test
    void handle_outcomeAppliedBeforeFinalRoundClosed_deferisScoringUntilActionFactsReady() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var eraNumber = 1;

        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
        contextRepository.recordChainFact(gameId, playerId, chainId, ScoreReason.CHAIN_LINK_ADDED, eraNumber);

        // The last OutcomeApplied for the era arrives before round 3 has closed — this simulates
        // timeline-service resolving faster than the action module's final-round bundle
        // (EraActionFactsFinalized) can be raised.
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        assertThat(playerScoreRepository.findAllByGameId(gameId)).isEmpty();

        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new EraActionFactsFinalized(gameId, eraNumber, List.of(), List.of())));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> assertThat(score.playerId()).isEqualTo(playerId)));
    }

    @Test
    void handle_terminalBarrierBeforeOutcome_resolvesRallyAfterTheOutcomeArrives() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        prepareRallyDeclaration(gameId, eraNumber, playerId, eventId, winningOutcomeId);

        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        assertThat(playerScoreRepository.findAllByGameId(gameId)).isEmpty();

        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> {
                            assertThat(score.playerId()).isEqualTo(playerId);
                            assertThat(score.totalScore())
                                    .isEqualTo(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY.pointsDelta());
                            assertThat(score.history())
                                    .singleElement()
                                    .satisfies(entry -> assertThat(entry.reason())
                                            .isEqualTo(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY));
                        }));
    }

    @Test
    void handle_outcomeBeforeTerminalBarrier_resolvesMomentumAfterTheBarrierArrives() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);
        contextRepository.upsertActivistDeclaration(new io.github.temporalrift.game.shared.ActivistDeclarationRecorded(
                gameId, eraNumber, 1, playerId, SpecialAction.MOMENTUM, eventId, winningOutcomeId));
        contextRepository.markActionFactsReady(gameId, eraNumber);

        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        assertThat(playerScoreRepository.findAllByGameId(gameId)).isEmpty();

        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> assertThat(score.totalScore())
                                .isEqualTo(ScoreReason.DECLARED_OUTCOME_WON.pointsDelta())));
    }

    @Test
    void handle_outcomesBeforeDuplicateRevisionistFinalization_awardsTheFactOnce() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.REVISIONISTS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);

        // Timeline resolution is allowed to finish before the final action-round bundle arrives.
        // The later bundle must resolve against the persisted terminal facts, and duplicate delivery
        // must remain a single score entry.
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        assertThat(playerScoreRepository.findAllByGameId(gameId)).isEmpty();

        var finalization = new EraActionFactsFinalized(
                gameId,
                eraNumber,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new EraActionFactsFinalized.RevisionistFact(
                        playerId, SpecialAction.REWRITE, eventId, winningOutcomeId)));
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(finalization));
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(finalization));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> {
                            assertThat(score.totalScore()).isEqualTo(ScoreReason.SECRET_OUTCOME_WON.pointsDelta());
                            assertThat(score.history())
                                    .extracting(ScoreEntry::reason)
                                    .containsExactly(ScoreReason.SECRET_OUTCOME_WON);
                        }));
    }

    private void prepareRallyDeclaration(
            UUID gameId, int eraNumber, UUID playerId, UUID eventId, UUID targetOutcomeId) {
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);
        contextRepository.upsertActivistDeclaration(new io.github.temporalrift.game.shared.ActivistDeclarationRecorded(
                gameId, eraNumber, 1, playerId, SpecialAction.RALLY, eventId, targetOutcomeId));
        contextRepository.markActionFactsReady(gameId, eraNumber);
    }

    private static InboundEnvelope terminalBarrierEnvelope(
            UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId) {
        var barrier = new EraResolutionCompleted(
                gameId,
                eraNumber,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, winningOutcomeId)));
        return new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.EraResolutionCompleted",
                gameId,
                "Game",
                gameId,
                Instant.now(),
                1,
                barrier);
    }

    private static InboundEnvelope outcomeEnvelope(UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId) {
        var outcome = new OutcomeApplied(gameId, eraNumber, eventId, winningOutcomeId, List.of());
        return new InboundEnvelope(
                UUID.randomUUID(), "timeline.OutcomeApplied", gameId, "FutureEvent", gameId, Instant.now(), 1, outcome);
    }

    private Integer scoresUpdatedOutboxRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication "
                        + "WHERE serialized_event LIKE '%Scoringpublish-scores-updated-out%'",
                Integer.class);
    }
}
