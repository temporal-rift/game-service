package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;

@SpringBootTest
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

    @Test
    void handle_lastOutcomeAppliedForEra_updatesPlayerScoreAndPublishesScoresUpdated() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var eraNumber = 1;

        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);
        contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
        contextRepository.recordChainFact(gameId, playerId, chainId, ScoreReason.CHAIN_LINK_ADDED, eraNumber);

        var outcome = new OutcomeApplied(gameId, eraNumber, UUID.randomUUID(), UUID.randomUUID(), List.of());
        var envelope = EventEnvelope.create(gameId, "FutureEvent", gameId, 1, outcome);

        consumer.handle(envelope);

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

    private Integer scoresUpdatedOutboxRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE '%ScoresUpdated%'", Integer.class);
    }
}
