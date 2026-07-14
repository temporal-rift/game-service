package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

@ExtendWith(MockitoExtension.class)
class PlayerScoreRepositoryAdapterTest {

    @Mock
    PlayerScoreJpaRepository jpaRepository;

    @Mock
    PlayerScoreHistoryJpaRepository historyJpaRepository;

    @InjectMocks
    PlayerScoreRepositoryAdapter adapter;

    @Test
    void findAllByGameIdWithLock_reconstitutesFromPersistedScoreAndHistory() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var scoreId = UUID.randomUUID();

        var entity = new PlayerScoreJpaEntity();
        entity.setId(scoreId);
        entity.setGameId(gameId);
        entity.setPlayerId(playerId);
        entity.setFaction(Faction.WEAVERS.name());
        entity.setTotalScore(12);
        given(jpaRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of(entity));

        var historyRow1 = PlayerScoreHistoryJpaEntity.fromDomain(
                scoreId, gameId, playerId, new ScoreEntry(1, ScoreReason.CHAIN_LINK_ADDED, 2, 2));
        var historyRow2 = PlayerScoreHistoryJpaEntity.fromDomain(
                scoreId, gameId, playerId, new ScoreEntry(2, ScoreReason.CHAIN_COMPLETED, 10, 12));
        given(historyJpaRepository.findAllByPlayerScoreIdOrderByEraNumberAsc(scoreId))
                .willReturn(List.of(historyRow1, historyRow2));

        var scores = adapter.findAllByGameIdWithLock(gameId);

        assertThat(scores).singleElement().satisfies(score -> {
            assertThat(score.id()).isEqualTo(scoreId);
            assertThat(score.gameId()).isEqualTo(gameId);
            assertThat(score.playerId()).isEqualTo(playerId);
            assertThat(score.faction()).isEqualTo(Faction.WEAVERS);
            assertThat(score.totalScore()).isEqualTo(12);
            assertThat(score.history())
                    .extracting(e -> e.reason())
                    .containsExactly(ScoreReason.CHAIN_LINK_ADDED, ScoreReason.CHAIN_COMPLETED);
        });
    }

    @Test
    void saveAll_persistsFullHistoryForNewAggregate() {
        var score = new PlayerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.ACTIVISTS);
        score.apply(1, ScoreReason.DECLARED_OUTCOME_WON);
        given(jpaRepository.findById(score.id())).willReturn(Optional.empty());
        given(historyJpaRepository.countByPlayerScoreId(score.id())).willReturn(0L);

        adapter.saveAll(List.of(score));

        var entityCaptor = ArgumentCaptor.forClass(PlayerScoreJpaEntity.class);
        then(jpaRepository).should().save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getId()).isEqualTo(score.id());
        assertThat(entityCaptor.getValue().getGameId()).isEqualTo(score.gameId());
        assertThat(entityCaptor.getValue().getPlayerId()).isEqualTo(score.playerId());
        assertThat(entityCaptor.getValue().getFaction()).isEqualTo(Faction.ACTIVISTS.name());
        assertThat(entityCaptor.getValue().getTotalScore()).isEqualTo(4);

        var historyCaptor = ArgumentCaptor.forClass(List.class);
        then(historyJpaRepository).should().saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue()).hasSize(1);
    }

    @Test
    void saveAll_onlyPersistsNewHistoryEntriesForExistingAggregate() {
        var scoreId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var score = PlayerScore.reconstitute(
                scoreId,
                gameId,
                playerId,
                Faction.WEAVERS,
                2,
                List.of(new ScoreEntry(1, ScoreReason.CHAIN_LINK_ADDED, 2, 2)));
        score.apply(2, ScoreReason.CHAIN_COMPLETED);

        var existingEntity = new PlayerScoreJpaEntity();
        existingEntity.setId(scoreId);
        existingEntity.setGameId(gameId);
        existingEntity.setPlayerId(playerId);
        given(jpaRepository.findById(scoreId)).willReturn(Optional.of(existingEntity));
        given(historyJpaRepository.countByPlayerScoreId(scoreId)).willReturn(1L);

        adapter.saveAll(List.of(score));

        var historyCaptor = ArgumentCaptor.forClass(List.class);
        then(historyJpaRepository).should().saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue()).hasSize(1);
        var savedRow = (PlayerScoreHistoryJpaEntity) historyCaptor.getValue().get(0);
        assertThat(savedRow.getReason()).isEqualTo(ScoreReason.CHAIN_COMPLETED.name());
    }

    @Test
    void findAllByGameId_usesUnlockedQuery() {
        var gameId = UUID.randomUUID();
        given(jpaRepository.findAllByGameId(gameId)).willReturn(List.of());

        adapter.findAllByGameId(gameId);

        then(jpaRepository).should().findAllByGameId(gameId);
        then(jpaRepository).should(never()).findAllByGameIdWithLock(any());
    }
}
