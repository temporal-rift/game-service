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
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActionRoundClosed;
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
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "A", List.of(), false),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "B", List.of(), false),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "C", List.of(), false)));

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
                        false)));

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
                gameId, eraNumber, List.of(new EventsDrawn.FutureEvent(eventId, "A", List.of(), false)));

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
                        false)));

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
    void actionRoundClosedFinalRound_marksActionFactsReady() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;

        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new ActionRoundClosed(gameId, eraNumber, 3, "ALL_SUBMITTED", 3)));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(contextRepository.actionFactsReady(gameId, eraNumber))
                        .isTrue());
    }

    @Test
    void actionRoundClosedNonFinalRound_doesNotMarkActionFactsReady() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;

        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new ActionRoundClosed(gameId, eraNumber, 1, "ALL_SUBMITTED", 3)));

        // No await here on purpose: this asserts the negative outcome, so give the (fast, in-process)
        // async listener a moment to have run and then check it did nothing.
        await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(contextRepository.actionFactsReady(gameId, eraNumber))
                        .isFalse());
    }
}
