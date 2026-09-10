package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.scoring.PlayerScoreQuery;
import io.github.temporalrift.game.scoring.application.command.AwardUnidentifiedFactionScores;
import io.github.temporalrift.game.scoring.application.query.PlayerScoreQueryService;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionRevealed;

/**
 * The end-game saga awards the unidentified-faction bonus and then snapshots the final scores for
 * {@code GameEnded} inside one transaction. {@code GameEnded.finalScores} is the only carrier of that
 * bonus to downstream consumers, so the snapshot has to observe the award — which a same-transaction
 * read cannot be assumed to do, since the award writes through a native upsert that leaves any
 * already-managed entity untouched.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostgresTestcontainersConfiguration.class,
    PlayerScoreRepositoryAdapter.class,
    EndGameScoreFactRepositoryAdapter.class,
    FactionIdentificationRepositoryAdapter.class,
    AwardUnidentifiedFactionScores.class,
    PlayerScoreQueryService.class
})
class EndGameScoreSnapshotIT {

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    AwardUnidentifiedFactionScores awardUnidentifiedFactionScores;

    @Autowired
    PlayerScoreQuery playerScoreQuery;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void finalScoreSnapshotTakenAfterTheAwardInTheSameTransactionIncludesTheBonus() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var score = new PlayerScore(UUID.randomUUID(), gameId, playerId, Faction.REVISIONISTS);
        score.apply(1, ScoreReason.SECRET_OUTCOME_WON);
        transactionTemplate.executeWithoutResult(_ -> playerScoreRepository.saveAll(List.of(score)));

        var reveal = new FactionRevealed(
                gameId, List.of(new FactionRevealed.PlayerFactionResult(playerId, Faction.REVISIONISTS.name())));

        // Exactly the sequence EndGameSagaImpl.start() runs: award, then snapshot, one transaction.
        var snapshot = transactionTemplate.execute(_ -> {
            awardUnidentifiedFactionScores.award(reveal);
            return playerScoreQuery.getScores(gameId);
        });

        assertThat(snapshot)
                .singleElement()
                .extracting(io.github.temporalrift.game.shared.GameEnded.PlayerScoreResult::score)
                .as("the published final score carries the end-game bonus, not the pre-award total")
                .isEqualTo(
                        ScoreReason.SECRET_OUTCOME_WON.pointsDelta() + ScoreReason.FACTION_UNIDENTIFIED.pointsDelta());
    }
}
