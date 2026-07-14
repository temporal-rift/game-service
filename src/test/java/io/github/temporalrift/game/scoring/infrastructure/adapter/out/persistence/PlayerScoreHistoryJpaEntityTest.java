package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

class PlayerScoreHistoryJpaEntityTest {

    @Test
    void fromDomain_mapsAllFieldsOntoEntity() {
        var playerScoreId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var entry = new ScoreEntry(3, ScoreReason.CHAIN_COMPLETED, 10, 12);

        var entity = PlayerScoreHistoryJpaEntity.fromDomain(playerScoreId, gameId, playerId, entry);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getPlayerScoreId()).isEqualTo(playerScoreId);
        assertThat(entity.getGameId()).isEqualTo(gameId);
        assertThat(entity.getPlayerId()).isEqualTo(playerId);
        assertThat(entity.getEraNumber()).isEqualTo(3);
        assertThat(entity.getReason()).isEqualTo(ScoreReason.CHAIN_COMPLETED.name());
        assertThat(entity.getPointsDelta()).isEqualTo(10);
        assertThat(entity.getNewTotal()).isEqualTo(12);
    }

    @Test
    void toDomain_reconstitutesScoreEntry() {
        var entry = new ScoreEntry(2, ScoreReason.CHAIN_BROKEN, -3, 5);
        var entity =
                PlayerScoreHistoryJpaEntity.fromDomain(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), entry);

        assertThat(entity.toDomain()).isEqualTo(entry);
    }
}
