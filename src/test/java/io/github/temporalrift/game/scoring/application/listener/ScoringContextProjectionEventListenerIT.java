package io.github.temporalrift.game.scoring.application.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.CarryOverState;
import io.github.temporalrift.game.shared.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ScoringContextProjectionEventListenerIT {

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    EraScoringContextRepository contextRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    EraScoringCompletionChecker completionChecker;

    @Test
    void factionAssigned_populatesScoringContextPlayer() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ ->
                applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.PROPHETS.name())));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(
                                contextRepository.getRequired(gameId, 1).players())
                        .containsExactly(new PlayerFaction(playerId, Faction.PROPHETS)));
    }

    @Test
    void eventsDrawn_populatesExpectedOutcomeCount() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;
        var event = new EventsDrawn(
                gameId,
                eraNumber,
                List.of(
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "A", List.of(), CarryOverState.FRESH),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "B", List.of(), CarryOverState.FRESH),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "C", List.of(), CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(event));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(contextRepository.expectedOutcomeCount(gameId, eraNumber))
                        .isEqualTo(3));
    }

    @Test
    void expectedOutcomeCount_notYetDrawn_throws() {
        var gameId = UUID.randomUUID();

        assertThatThrownBy(() -> contextRepository.expectedOutcomeCount(gameId, 99))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }

    @Test
    void eventsDrawn_populatesPerEventOutcomeBaseline() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId,
                eraNumber,
                List.of(new EventsDrawn.FutureEvent(
                        eventId,
                        "A",
                        List.of(
                                new EventsDrawn.Outcome(UUID.randomUUID(), "1", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "2", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "3", 34)),
                        CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> {
            applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.PROPHETS.name()));
            applicationEventPublisher.publishEvent(event);
        });

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(
                                contextRepository.getRequired(gameId, eraNumber).eventOutcomes())
                        .containsExactly(new EventOutcomeFact(eventId, null, null, 3, 3)));
    }

    @Test
    void foresightDeclared_populatesWrittenOutcome() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId, eraNumber, List.of(new EventsDrawn.FutureEvent(eventId, "A", List.of(), CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> {
            applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.PROPHETS.name()));
            applicationEventPublisher.publishEvent(event);
        });
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new ForesightDeclared(gameId, eraNumber, eventId, outcomeId, playerId)));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(
                                contextRepository.getRequired(gameId, eraNumber).eventOutcomes())
                        .singleElement()
                        .satisfies(fact -> assertThat(fact.writtenOutcomeId()).isEqualTo(outcomeId)));
    }

    @Test
    void outcomeAnnihilated_reducesEndingOutcomeCount() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId,
                eraNumber,
                List.of(new EventsDrawn.FutureEvent(
                        eventId,
                        "A",
                        List.of(
                                new EventsDrawn.Outcome(outcomeId, "1", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "2", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "3", 34)),
                        CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> {
            applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.ERASERS.name()));
            applicationEventPublisher.publishEvent(event);
        });
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new OutcomeAnnihilated(gameId, eraNumber, eventId, outcomeId, playerId)));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(
                                contextRepository.getRequired(gameId, eraNumber).eventOutcomes())
                        .singleElement()
                        .satisfies(fact -> assertThat(fact.endingOutcomeCount()).isEqualTo(2)));
    }

    @Test
    void eraActionFactsFinalized_marksActionFactsReady() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;

        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new EraActionFactsFinalized(gameId, eraNumber, List.of(), List.of())));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(contextRepository.actionFactsReady(gameId, eraNumber))
                        .isTrue());
    }

    @Test
    void eraActionFactsFinalized_bundledFactsAloneAreSufficient_noPerSubmissionEventNeeded() {
        // Proves the fix for the race where the final round's own ForesightDeclared/OutcomeAnnihilated
        // listener could still be in flight when scoring decides the era is ready: this test never
        // publishes those per-submission events at all, only the close-time bundle, and the fact still
        // lands correctly.
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId, eraNumber, List.of(new EventsDrawn.FutureEvent(eventId, "A", List.of(), CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> {
            applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.PROPHETS.name()));
            applicationEventPublisher.publishEvent(event);
        });
        transactionTemplate.executeWithoutResult(
                _ -> applicationEventPublisher.publishEvent(new EraActionFactsFinalized(
                        gameId,
                        eraNumber,
                        List.of(new EraActionFactsFinalized.ForesightFact(eventId, outcomeId, playerId)),
                        List.of())));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> {
                    assertThat(contextRepository.actionFactsReady(gameId, eraNumber))
                            .isTrue();
                    assertThat(contextRepository.getRequired(gameId, eraNumber).eventOutcomes())
                            .singleElement()
                            .satisfies(
                                    fact -> assertThat(fact.writtenOutcomeId()).isEqualTo(outcomeId));
                });
    }

    @Test
    void eraActionFactsFinalized_appliesBundledForesightAndAnnihilationFacts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();
        var annihilatedOutcomeId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId,
                eraNumber,
                List.of(new EventsDrawn.FutureEvent(
                        eventId,
                        "A",
                        List.of(
                                new EventsDrawn.Outcome(annihilatedOutcomeId, "1", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "2", 33),
                                new EventsDrawn.Outcome(UUID.randomUUID(), "3", 34)),
                        CarryOverState.FRESH)));

        transactionTemplate.executeWithoutResult(_ -> {
            applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, Faction.PROPHETS.name()));
            applicationEventPublisher.publishEvent(event);
        });
        transactionTemplate.executeWithoutResult(
                _ -> applicationEventPublisher.publishEvent(new EraActionFactsFinalized(
                        gameId,
                        eraNumber,
                        List.of(new EraActionFactsFinalized.ForesightFact(eventId, writtenOutcomeId, playerId)),
                        List.of(new EraActionFactsFinalized.AnnihilationFact(
                                eventId, annihilatedOutcomeId, playerId)))));

        await().atMost(Duration.ofSeconds(10))
                .ignoreException(EraScoringContextNotFoundException.class)
                .untilAsserted(() -> assertThat(
                                contextRepository.getRequired(gameId, eraNumber).eventOutcomes())
                        .singleElement()
                        .satisfies(fact -> {
                            assertThat(fact.writtenOutcomeId()).isEqualTo(writtenOutcomeId);
                            assertThat(fact.endingOutcomeCount()).isEqualTo(2);
                        }));
    }

    @Test
    void tryComplete_calledWithNoAmbientTransaction_completesRatherThanThrowing() {
        // Reproduces ScoringCompletionSweep's exact calling context: a plain @Scheduled method has no
        // surrounding transaction, unlike every other caller of tryComplete (Kafka listeners,
        // @ApplicationModuleListener). tryMarkScoringComplete below requires one (MANDATORY) — this
        // test proves EraScoringCompletionChecker.tryComplete's own @Transactional supplies it rather
        // than throwing IllegalTransactionStateException. Deliberately not wrapped in
        // transactionTemplate.executeWithoutResult, unlike every setup call above.
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eraNumber = 1;
        transactionTemplate.executeWithoutResult(_ -> {
            contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);
            contextRepository.markActionFactsReady(gameId, eraNumber);
            contextRepository.saveEraResolutionCompleted(new EraResolutionCompleted(gameId, eraNumber, List.of()));
        });
        assertThat(contextRepository.findResolvedErasNotYetScored())
                .contains(new PendingEraScoringCompletion(gameId, eraNumber));

        completionChecker.tryComplete(gameId, eraNumber);

        assertThat(contextRepository.findResolvedErasNotYetScored())
                .noneMatch(pending -> pending.gameId().equals(gameId));
    }
}
