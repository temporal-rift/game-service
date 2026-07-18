package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.session.domain.event.EventsDrawn;
import io.github.temporalrift.game.session.domain.event.FactionAssigned;
import io.github.temporalrift.game.shared.Faction;

/**
 * Proves {@link ActionStateProjectionEventListener} actually fires from the typed in-process events
 * {@code StartGameSagaImpl}/{@code EraSagaImpl} publish — regression coverage for the dormant-listener bug
 * (issue #56), where these handlers existed but were never triggered because the session module only
 * published Kafka envelopes, never the typed Spring events {@code @ApplicationModuleListener} needs.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ActionStateProjectionEventListenerIT {

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    FutureEventDefinitionPort futureEventDefinitionPort;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void factionAssigned_publishedAsTypedEvent_updatesPlayerStateFaction() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(
                _ -> applicationEventPublisher.publishEvent(new FactionAssigned(gameId, playerId, "WEAVERS")));

        var playerState = awaitPlayerState(gameId, playerId);
        assertThat(playerState.faction()).isEqualTo(Faction.WEAVERS);
    }

    @Test
    void eventsDrawn_publishedAsTypedEvent_populatesFutureEventCatalogForEra() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var event = new EventsDrawn.FutureEvent(
                eventId, "Test Event", List.of(new EventsDrawn.Outcome(outcomeId, "Outcome", 100)), false);

        transactionTemplate.executeWithoutResult(
                _ -> applicationEventPublisher.publishEvent(new EventsDrawn(gameId, eraNumber, List.of(event))));

        var definitions = awaitEventDefinitions(gameId, eraNumber);
        assertThat(definitions).hasSize(1);
        assertThat(definitions.getFirst().eventId()).isEqualTo(eventId);
        assertThat(definitions.getFirst().outcomes()).hasSize(1);
        assertThat(definitions.getFirst().outcomes().getFirst().outcomeId()).isEqualTo(outcomeId);
    }

    private PlayerState awaitPlayerState(UUID gameId, UUID playerId) {
        return await().atMost(Duration.ofSeconds(10))
                .until(() -> playerStateRepository.findByGameIdAndPlayerId(gameId, playerId), Optional::isPresent)
                .orElseThrow();
    }

    private List<FutureEventDefinitionPort.EventDefinition> awaitEventDefinitions(UUID gameId, int eraNumber) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber),
                        definitions -> !definitions.isEmpty());
    }
}
