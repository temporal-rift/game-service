package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.AnnihilationResolvedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OutcomeAppliedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.SpecialRejectedPayload;
import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.scoring.FactionObjectiveQuery;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@GameServiceIntegrationTest
class TimelineScoringKafkaConsumerIT {

    @Autowired
    TimelineScoringKafkaConsumer consumer;

    @Autowired
    EraScoringContextRepository contextRepository;

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    FactionObjectiveQuery factionObjectiveQuery;

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
            assertThat(score.totalScore()).isEqualTo(2);
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
                            assertThat(score.totalScore()).isEqualTo(6);
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
        contextRepository.upsertActivistDeclaration(
                new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded(
                        gameId, eraNumber, 1, playerId, SpecialAction.MOMENTUM, eventId, winningOutcomeId));
        contextRepository.markActionFactsReady(gameId, eraNumber);

        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        assertThat(playerScoreRepository.findAllByGameId(gameId)).isEmpty();

        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> assertThat(score.totalScore()).isEqualTo(5)));
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
                List.of(new EraActionFactsFinalized.RevisionistFact(
                        playerId, SpecialAction.REWRITE, eventId, winningOutcomeId)));
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(finalization));
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(finalization));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(playerScoreRepository.findAllByGameId(gameId))
                        .singleElement()
                        .satisfies(score -> {
                            assertThat(score.totalScore()).isEqualTo(4);
                            assertThat(score.history())
                                    .extracting(ScoreEntry::reason)
                                    .containsExactly(ScoreReason.SECRET_OUTCOME_WON);
                        }));
    }

    @Test
    void handle_annihilationsBeforeBarrier_creditOnlyTheErasedLeaderOnce() {
        var gameId = UUID.randomUUID();
        var leaderEraser = UUID.randomUUID();
        var trailingEraser = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, leaderEraser, Faction.ERASERS);
        contextRepository.upsertPlayerFaction(gameId, trailingEraser, Faction.ERASERS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
        contextRepository.markActionFactsReady(gameId, eraNumber);

        var leaderErased = annihilationEnvelope(gameId, eraNumber, leaderEraser, eventId, true, true);
        consumer.handle(leaderErased);
        consumer.handle(leaderErased);
        consumer.handle(annihilationEnvelope(gameId, eraNumber, trailingEraser, eventId, true, false));
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        assertThat(playerScoreRepository.findAllByGameId(gameId))
                .filteredOn(score -> score.totalScore() != 0)
                .singleElement()
                .satisfies(score -> {
                    assertThat(score.playerId()).isEqualTo(leaderEraser);
                    assertThat(score.totalScore()).isEqualTo(3);
                    assertThat(score.history())
                            .singleElement()
                            .satisfies(entry -> assertThat(entry.reason()).isEqualTo(ScoreReason.ANNIHILATED_OUTCOME));
                });
    }

    @Test
    void handle_rejectedFinalAnnihilate_scoresNothingAndDoesNotAdvanceObjective() {
        var gameId = UUID.randomUUID();
        var eraser = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        prepareEraserScoring(gameId, eraNumber, eraser);

        consumer.handle(rejectedSpecialEnvelope(
                gameId,
                eraNumber,
                eraser,
                eventId,
                UUID.randomUUID(),
                GeneratedChannelContract.SpecialAction.ANNIHILATE,
                "LAST_ELIGIBLE_OUTCOME"));
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        assertEraserScoreAndProgress(gameId, eraNumber, eraser, 0, 0);
    }

    @Test
    void handle_acceptedAndRejectedAnnihilates_creditsOnlyTheAcceptedErasure() {
        var gameId = UUID.randomUUID();
        var acceptedEraser = UUID.randomUUID();
        var rejectedEraser = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        prepareEraserScoring(gameId, eraNumber, acceptedEraser);
        contextRepository.upsertPlayerFaction(gameId, rejectedEraser, Faction.ERASERS);

        consumer.handle(annihilationEnvelope(gameId, eraNumber, acceptedEraser, eventId, true, true));
        consumer.handle(rejectedSpecialEnvelope(
                gameId,
                eraNumber,
                rejectedEraser,
                eventId,
                UUID.randomUUID(),
                GeneratedChannelContract.SpecialAction.ANNIHILATE,
                "LAST_ELIGIBLE_OUTCOME"));
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        assertThat(playerScoreRepository.findAllByGameId(gameId)).hasSize(2);
        assertEraserScoreAndProgress(gameId, eraNumber, acceptedEraser, 3, 1);
        assertEraserScoreAndProgress(gameId, eraNumber, rejectedEraser, 0, 0);
    }

    @Test
    void handle_cascadeAgainstRejectedAnnihilate_scoresNothing() {
        var gameId = UUID.randomUUID();
        var eraser = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        prepareEraserScoring(gameId, eraNumber, eraser);

        consumer.handle(rejectedSpecialEnvelope(
                gameId,
                eraNumber,
                eraser,
                eventId,
                targetOutcomeId,
                GeneratedChannelContract.SpecialAction.ANNIHILATE,
                "LAST_ELIGIBLE_OUTCOME"));
        consumer.handle(rejectedSpecialEnvelope(
                gameId,
                eraNumber,
                eraser,
                eventId,
                targetOutcomeId,
                GeneratedChannelContract.SpecialAction.CASCADE,
                "TARGET_NOT_ERASED"));
        consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));

        assertEraserScoreAndProgress(gameId, eraNumber, eraser, 0, 0);
    }

    @Test
    void handle_oneLeaderAnnihilatePerEra_totalsNineAfterThreeEras() {
        var gameId = UUID.randomUUID();
        var eraser = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, eraser, Faction.ERASERS);

        for (var eraNumber = 1; eraNumber <= 3; eraNumber++) {
            var eventId = UUID.randomUUID();
            var winningOutcomeId = UUID.randomUUID();
            contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
            contextRepository.markActionFactsReady(gameId, eraNumber);
            consumer.handle(annihilationEnvelope(gameId, eraNumber, eraser, eventId, true, true));
            consumer.handle(outcomeEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
            consumer.handle(terminalBarrierEnvelope(gameId, eraNumber, eventId, winningOutcomeId));
        }

        assertThat(playerScoreRepository.findAllByGameId(gameId))
                .singleElement()
                .satisfies(score -> {
                    assertThat(score.totalScore()).isEqualTo(9).isLessThan(20);
                    assertThat(score.history())
                            .extracting(ScoreEntry::reason)
                            .containsOnly(ScoreReason.ANNIHILATED_OUTCOME);
                });
    }

    private static Message<Object> annihilationEnvelope(
            UUID gameId, int eraNumber, UUID playerId, UUID eventId, boolean erased, boolean wasLeading) {
        var resolution = new AnnihilationResolvedPayload(
                gameId, eraNumber, 1, playerId, eventId, UUID.randomUUID(), erased, wasLeading);
        return message("AnnihilationResolved", gameId, "FutureEvent", resolution);
    }

    private static Message<Object> rejectedSpecialEnvelope(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID eventId,
            UUID outcomeId,
            GeneratedChannelContract.SpecialAction specialAction,
            String reason) {
        return message(
                "SpecialRejected",
                gameId,
                "FutureEvent",
                new SpecialRejectedPayload(
                        gameId, eraNumber, playerId, specialAction, null, eventId, outcomeId, reason));
    }

    private void prepareEraserScoring(UUID gameId, int eraNumber, UUID eraser) {
        contextRepository.upsertPlayerFaction(gameId, eraser, Faction.ERASERS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
        contextRepository.markActionFactsReady(gameId, eraNumber);
    }

    private void assertEraserScoreAndProgress(
            UUID gameId, int eraNumber, UUID eraser, int expectedScore, int expectedAnnihilations) {
        assertThat(playerScoreRepository.findAllByGameId(gameId))
                .filteredOn(score -> score.playerId().equals(eraser))
                .singleElement()
                .satisfies(score -> {
                    assertThat(score.playerId()).isEqualTo(eraser);
                    assertThat(score.totalScore()).isEqualTo(expectedScore);
                    assertThat(score.history())
                            .filteredOn(entry -> entry.reason() == ScoreReason.ANNIHILATED_OUTCOME)
                            .hasSize(expectedAnnihilations);
                });
        assertThat(factionObjectiveQuery.evaluate(gameId, eraNumber))
                .filteredOn(progress -> progress.playerId().equals(eraser))
                .singleElement()
                .satisfies(progress -> {
                    assertThat(progress.playerId()).isEqualTo(eraser);
                    assertThat(progress.progressCount()).isEqualTo(expectedAnnihilations);
                    assertThat(progress.objectiveMet()).isFalse();
                });
    }

    private void prepareRallyDeclaration(
            UUID gameId, int eraNumber, UUID playerId, UUID eventId, UUID targetOutcomeId) {
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);
        contextRepository.upsertActivistDeclaration(
                new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded(
                        gameId, eraNumber, 1, playerId, SpecialAction.RALLY, eventId, targetOutcomeId));
        contextRepository.markActionFactsReady(gameId, eraNumber);
    }

    private static Message<Object> terminalBarrierEnvelope(
            UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId) {
        var barrier = new EraResolutionCompletedPayload(
                gameId, eraNumber, List.of(new EraTerminalResolution(eventId, 0, "OUTCOME_APPLIED", winningOutcomeId)));
        return message("EraResolutionCompleted", gameId, "Game", barrier);
    }

    private static Message<Object> outcomeEnvelope(UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId) {
        var outcome = new OutcomeAppliedPayload(gameId, eraNumber, eventId, winningOutcomeId, List.of());
        return message("OutcomeApplied", gameId, "FutureEvent", outcome);
    }

    /** The published wire shape: envelope metadata in the headers, only the typed payload in the body. */
    private static Message<Object> message(String eventType, UUID gameId, String aggregateType, Object payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", gameId.toString())
                .setHeader("aggregateType", aggregateType)
                .setHeader("gameId", gameId.toString())
                .setHeader("occurredAt", Instant.now().toString())
                .setHeader("version", "1")
                .build();
    }

    private Integer scoresUpdatedOutboxRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication " + "WHERE serialized_event LIKE '%\"ScoresUpdated\"%'",
                Integer.class);
    }
}
