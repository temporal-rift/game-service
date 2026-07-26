package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository.CurrentScoreRow;
import io.github.temporalrift.game.shared.Faction;

@ExtendWith(MockitoExtension.class)
class ScoringReadRepositoryAdapterTest {

    static final UUID GAME_ID = UUID.randomUUID();
    // Fixed, not random: findCurrentScores/findScoreHistory sort by playerId, so tests assert on
    // an order that must be deterministic across runs.
    static final UUID PLAYER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAYER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    PlayerScoreJpaRepository playerScoreJpaRepository;

    @Mock
    PlayerScoreHistoryJpaRepository historyJpaRepository;

    @Mock
    ScoringPlayerJpaRepository playerJpaRepository;

    @InjectMocks
    ScoringReadRepositoryAdapter adapter;

    @Test
    void findCurrentScores_nullGameId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.findCurrentScores(null));
    }

    @Test
    void findCurrentScores_noScores_returnsEmptyListWithoutFurtherLookups() {
        given(playerScoreJpaRepository.findAllByGameId(GAME_ID)).willReturn(List.of());

        var rows = adapter.findCurrentScores(GAME_ID);

        assertThat(rows).isEmpty();
        then(playerJpaRepository).should(never()).findAllByGameId(GAME_ID);
        then(historyJpaRepository).should(never()).findMaxEraNumberByGameId(GAME_ID);
    }

    @Test
    void findCurrentScores_mapsSortedRowsWithNamesAndMaxEra() {
        var entity1 = scoreEntity(PLAYER_1, Faction.ERASERS, 12);
        var entity2 = scoreEntity(PLAYER_2, Faction.PROPHETS, 8);
        given(playerScoreJpaRepository.findAllByGameId(GAME_ID)).willReturn(List.of(entity2, entity1));
        given(playerJpaRepository.findAllByGameId(GAME_ID)).willReturn(List.of(playerEntity(PLAYER_1, "Ada")));
        given(historyJpaRepository.findMaxEraNumberByGameId(GAME_ID)).willReturn(3);

        var rows = adapter.findCurrentScores(GAME_ID);

        assertThat(rows).extracting(CurrentScoreRow::playerId).containsExactly(PLAYER_1, PLAYER_2);
        assertThat(rows).allSatisfy(row -> assertThat(row.eraNumber()).isEqualTo(3));
        assertThat(rows.get(0).playerName()).isEqualTo("Ada");
        assertThat(rows.get(0).faction()).isEqualTo(Faction.ERASERS);
        assertThat(rows.get(0).score()).isEqualTo(12);
        // player 2 has no scoring_player row — falls back to an empty name rather than throwing
        assertThat(rows.get(1).playerName()).isEmpty();
        assertThat(rows.get(1).faction()).isEqualTo(Faction.PROPHETS);
    }

    @Test
    void findCurrentScores_noHistoryYet_defaultsEraToZero() {
        given(playerScoreJpaRepository.findAllByGameId(GAME_ID))
                .willReturn(List.of(scoreEntity(PLAYER_1, Faction.WEAVERS, 0)));
        given(playerJpaRepository.findAllByGameId(GAME_ID)).willReturn(List.of());
        given(historyJpaRepository.findMaxEraNumberByGameId(GAME_ID)).willReturn(null);

        var rows = adapter.findCurrentScores(GAME_ID);

        assertThat(rows)
                .singleElement()
                .satisfies(row -> assertThat(row.eraNumber()).isZero());
    }

    @Test
    void findScoreHistory_nullGameId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.findScoreHistory(null));
    }

    @Test
    void findScoreHistory_mapsRowsSortedByEraThenPlayerThenReason() {
        var era2Row = historyEntity(GAME_ID, 2, PLAYER_1, ScoreReason.FULFILLMENT_SUCCEEDED, 8);
        var era1RowB = historyEntity(GAME_ID, 1, PLAYER_2, ScoreReason.CHAIN_BROKEN, -3);
        var era1RowA = historyEntity(GAME_ID, 1, PLAYER_1, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
        given(historyJpaRepository.findAllByGameId(GAME_ID)).willReturn(List.of(era2Row, era1RowB, era1RowA));

        var rows = adapter.findScoreHistory(GAME_ID);

        assertThat(rows)
                .extracting(row -> row.eraNumber() + ":" + row.playerId())
                .containsExactly(1 + ":" + PLAYER_1, 1 + ":" + PLAYER_2, 2 + ":" + PLAYER_1);
        assertThat(rows.get(0).reason()).isEqualTo(ScoreReason.EVENT_RESOLVED_AS_WRITTEN);
        assertThat(rows.get(0).pointsDelta()).isEqualTo(4);
        assertThat(rows.get(0).gameId()).isEqualTo(GAME_ID);
    }

    private static PlayerScoreJpaEntity scoreEntity(UUID playerId, Faction faction, int totalScore) {
        var entity = new PlayerScoreJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(GAME_ID);
        entity.setPlayerId(playerId);
        entity.setFaction(faction.name());
        entity.setTotalScore(totalScore);
        return entity;
    }

    private static ScoringPlayerJpaEntity playerEntity(UUID playerId, String playerName) {
        var entity = new ScoringPlayerJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(GAME_ID);
        entity.setPlayerId(playerId);
        entity.setPlayerName(playerName);
        return entity;
    }

    private static PlayerScoreHistoryJpaEntity historyEntity(
            UUID gameId, int eraNumber, UUID playerId, ScoreReason reason, int pointsDelta) {
        return PlayerScoreHistoryJpaEntity.fromDomain(
                UUID.randomUUID(), gameId, playerId, new ScoreEntry(eraNumber, reason, pointsDelta, pointsDelta));
    }
}
