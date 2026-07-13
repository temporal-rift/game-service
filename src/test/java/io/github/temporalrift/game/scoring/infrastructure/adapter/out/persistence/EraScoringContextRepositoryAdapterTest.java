package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

@ExtendWith(MockitoExtension.class)
class EraScoringContextRepositoryAdapterTest {

    @Mock
    ScoringContextPlayerJpaRepository playerJpaRepository;

    @Mock
    ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository;

    @Mock
    ScoringContextChainFactJpaRepository chainFactJpaRepository;

    @InjectMocks
    EraScoringContextRepositoryAdapter adapter;

    @Test
    void getRequired_assemblesPlayersAndUnconsumedChainFacts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(playerId);
        playerEntity.setFaction(Faction.WEAVERS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var chainId = UUID.randomUUID();
        var chainFactEntity = new ScoringContextChainFactJpaEntity();
        chainFactEntity.setId(UUID.randomUUID());
        chainFactEntity.setGameId(gameId);
        chainFactEntity.setPlayerId(playerId);
        chainFactEntity.setChainId(chainId);
        chainFactEntity.setReason(ScoreReason.CHAIN_COMPLETED.name());
        chainFactEntity.setConsumed(false);
        given(chainFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId))
                .willReturn(List.of(chainFactEntity));

        var context = adapter.getRequired(gameId, 2);

        assertThat(context.gameId()).isEqualTo(gameId);
        assertThat(context.eraNumber()).isEqualTo(2);
        assertThat(context.players()).containsExactly(new PlayerFaction(playerId, Faction.WEAVERS));
        assertThat(context.chainFacts())
                .containsExactly(new ChainScoringFact(playerId, chainId, ScoreReason.CHAIN_COMPLETED));
        assertThat(context.eventOutcomes()).isEmpty();
        assertThat(context.actionFacts()).isEmpty();
    }

    @Test
    void getRequired_marksReturnedChainFactsAsConsumed() {
        var gameId = UUID.randomUUID();
        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(UUID.randomUUID());
        playerEntity.setFaction(Faction.ERASERS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var chainFactEntity = new ScoringContextChainFactJpaEntity();
        chainFactEntity.setId(UUID.randomUUID());
        chainFactEntity.setGameId(gameId);
        chainFactEntity.setPlayerId(UUID.randomUUID());
        chainFactEntity.setChainId(UUID.randomUUID());
        chainFactEntity.setReason(ScoreReason.CHAIN_LINK_ADDED.name());
        chainFactEntity.setConsumed(false);
        given(chainFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId))
                .willReturn(List.of(chainFactEntity));

        adapter.getRequired(gameId, 1);

        assertThat(chainFactEntity.isConsumed()).isTrue();
        var captor = ArgumentCaptor.forClass(List.class);
        then(chainFactJpaRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(chainFactEntity);
    }

    @Test
    void getRequired_throwsWhenNoPlayersFound() {
        var gameId = UUID.randomUUID();
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of());

        assertThatThrownBy(() -> adapter.getRequired(gameId, 1)).isInstanceOf(EraScoringContextNotFoundException.class);

        then(chainFactJpaRepository).should(never()).findAllByGameIdAndConsumedFalseWithLock(any());
    }

    @Test
    void expectedOutcomeCount_returnsStoredValue() {
        var gameId = UUID.randomUUID();
        var entity = new ScoringContextEraOutcomeExpectationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(gameId);
        entity.setEraNumber(3);
        entity.setExpectedOutcomeCount(3);
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 3))
                .willReturn(Optional.of(entity));

        assertThat(adapter.expectedOutcomeCount(gameId, 3)).isEqualTo(3);
    }

    @Test
    void expectedOutcomeCount_throwsWhenMissing() {
        var gameId = UUID.randomUUID();
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 1))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.expectedOutcomeCount(gameId, 1))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }

    @Test
    void upsertPlayerFaction_createsNewRowWhenMissing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerJpaRepository.findByGameIdAndPlayerId(gameId, playerId)).willReturn(Optional.empty());

        adapter.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);

        var captor = ArgumentCaptor.forClass(ScoringContextPlayerJpaEntity.class);
        then(playerJpaRepository).should().save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getGameId()).isEqualTo(gameId);
        assertThat(captor.getValue().getPlayerId()).isEqualTo(playerId);
        assertThat(captor.getValue().getFaction()).isEqualTo(Faction.ACTIVISTS.name());
    }

    @Test
    void upsertPlayerFaction_updatesExistingRow() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var existingId = UUID.randomUUID();
        var existing = new ScoringContextPlayerJpaEntity();
        existing.setId(existingId);
        existing.setGameId(gameId);
        existing.setPlayerId(playerId);
        existing.setFaction(Faction.PROPHETS.name());
        given(playerJpaRepository.findByGameIdAndPlayerId(gameId, playerId)).willReturn(Optional.of(existing));

        adapter.upsertPlayerFaction(gameId, playerId, Faction.REVISIONISTS);

        var captor = ArgumentCaptor.forClass(ScoringContextPlayerJpaEntity.class);
        then(playerJpaRepository).should().save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
        assertThat(captor.getValue().getFaction()).isEqualTo(Faction.REVISIONISTS.name());
    }

    @Test
    void upsertExpectedOutcomeCount_createsNewRowWhenMissing() {
        var gameId = UUID.randomUUID();
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.empty());

        adapter.upsertExpectedOutcomeCount(gameId, 2, 3);

        var captor = ArgumentCaptor.forClass(ScoringContextEraOutcomeExpectationJpaEntity.class);
        then(eraOutcomeExpectationJpaRepository).should().save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getGameId()).isEqualTo(gameId);
        assertThat(captor.getValue().getEraNumber()).isEqualTo(2);
        assertThat(captor.getValue().getExpectedOutcomeCount()).isEqualTo(3);
    }

    @Test
    void upsertExpectedOutcomeCount_updatesExistingRow() {
        var gameId = UUID.randomUUID();
        var existingId = UUID.randomUUID();
        var existing = new ScoringContextEraOutcomeExpectationJpaEntity();
        existing.setId(existingId);
        existing.setGameId(gameId);
        existing.setEraNumber(1);
        existing.setExpectedOutcomeCount(3);
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 1))
                .willReturn(Optional.of(existing));

        adapter.upsertExpectedOutcomeCount(gameId, 1, 4);

        var captor = ArgumentCaptor.forClass(ScoringContextEraOutcomeExpectationJpaEntity.class);
        then(eraOutcomeExpectationJpaRepository).should().save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
        assertThat(captor.getValue().getExpectedOutcomeCount()).isEqualTo(4);
    }
}
