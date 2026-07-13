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
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;

@SpringBootTest
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
                .untilAsserted(() -> assertThat(contextRepository.expectedOutcomeCount(gameId, eraNumber))
                        .isEqualTo(3));
    }

    @Test
    void expectedOutcomeCount_notYetDrawn_throws() {
        var gameId = UUID.randomUUID();

        assertThatThrownBy(() -> contextRepository.expectedOutcomeCount(gameId, 99))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }
}
