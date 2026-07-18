package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, PlayerScoreRepositoryAdapter.class})
class PlayerScorePersistenceIT {

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveAll_insertsNewPlayerScoreViaNativeUpsert() {
        var score = new PlayerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.WEAVERS);
        score.apply(1, ScoreReason.CHAIN_LINK_ADDED);

        transactionTemplate.executeWithoutResult(_ -> playerScoreRepository.saveAll(List.of(score)));

        var loaded = transactionTemplate.execute(_ -> playerScoreRepository.findAllByGameId(score.gameId()));
        assertThat(loaded).singleElement().satisfies(persisted -> {
            assertThat(persisted.id()).isEqualTo(score.id());
            assertThat(persisted.totalScore()).isEqualTo(2);
            assertThat(persisted.history()).hasSize(1);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void saveAll_secondCallForSamePlayerUpdatesInPlaceAndAppendsOnlyNewHistory() {
        // Suspends @DataJpaTest's default test-wrapping transaction so each
        // transactionTemplate.execute(...) below genuinely starts its own transaction
        // with a fresh persistence context, matching production: every Kafka message
        // gets its own @Transactional(REQUIRES_NEW). Without this, TransactionTemplate's
        // default PROPAGATION_REQUIRED just joins the already-active outer test
        // transaction, so a later read stays served from the stale in-memory entity
        // Hibernate cached before the native upsert query ran.
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var score = new PlayerScore(UUID.randomUUID(), gameId, playerId, Faction.ERASERS);
        score.apply(1, ScoreReason.ANNIHILATED_OUTCOME);
        transactionTemplate.executeWithoutResult(_ -> playerScoreRepository.saveAll(List.of(score)));
        var firstLoad = transactionTemplate
                .execute(_ -> playerScoreRepository.findAllByGameId(gameId))
                .getFirst();

        var reloaded = PlayerScore.reconstitute(
                firstLoad.id(), gameId, playerId, Faction.ERASERS, firstLoad.totalScore(), firstLoad.history());
        reloaded.apply(2, ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES);
        assertThat(reloaded.totalScore()).isEqualTo(8);
        transactionTemplate.executeWithoutResult(_ -> playerScoreRepository.saveAll(List.of(reloaded)));

        var rawTotalScore = jdbcTemplate.queryForObject(
                "SELECT total_score FROM player_score WHERE id = ?", Integer.class, firstLoad.id());
        assertThat(rawTotalScore).as("raw DB value via JdbcTemplate").isEqualTo(8);

        var secondLoad = transactionTemplate.execute(_ -> playerScoreRepository.findAllByGameId(gameId));
        assertThat(secondLoad).singleElement().satisfies(persisted -> {
            assertThat(persisted.id()).isEqualTo(firstLoad.id());
            assertThat(persisted.totalScore()).isEqualTo(8);
            assertThat(persisted.history()).hasSize(2);
        });
    }
}
