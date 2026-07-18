package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, EraScoringContextRepositoryAdapter.class})
class ScoringPersistenceIT {

    @Autowired
    EraScoringContextRepository contextRepository;

    @Autowired
    ScoringContextChainFactJpaRepository chainFactJpaRepository;

    @Test
    void getRequired_returnsPlayersAssembledFromUpsertedFactionAssignments() {
        var gameId = UUID.randomUUID();
        var player1 = UUID.randomUUID();
        var player2 = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, player1, Faction.ERASERS);
        contextRepository.upsertPlayerFaction(gameId, player2, Faction.PROPHETS);

        var context = contextRepository.getRequired(gameId, 1);

        assertThat(context.players())
                .containsExactlyInAnyOrder(
                        new PlayerFaction(player1, Faction.ERASERS), new PlayerFaction(player2, Faction.PROPHETS));
    }

    @Test
    void getRequired_throwsEraScoringContextNotFoundExceptionWhenNoPlayersExist() {
        var gameId = UUID.randomUUID();

        assertThatThrownBy(() -> contextRepository.getRequired(gameId, 1))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }

    @Test
    void upsertPlayerFaction_replacesFactionOnSecondCallForSamePlayer() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);

        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);

        var context = contextRepository.getRequired(gameId, 1);
        assertThat(context.players()).containsExactly(new PlayerFaction(playerId, Faction.WEAVERS));
    }

    @Test
    void expectedOutcomeCount_returnsUpsertedValueAndReplacesOnSecondCall() {
        var gameId = UUID.randomUUID();
        contextRepository.upsertExpectedOutcomeCount(gameId, 3, 3);

        assertThat(contextRepository.expectedOutcomeCount(gameId, 3)).isEqualTo(3);

        contextRepository.upsertExpectedOutcomeCount(gameId, 3, 4);
        assertThat(contextRepository.expectedOutcomeCount(gameId, 3)).isEqualTo(4);
    }

    @Test
    void expectedOutcomeCount_throwsWhenNoExpectationRecorded() {
        var gameId = UUID.randomUUID();

        assertThatThrownBy(() -> contextRepository.expectedOutcomeCount(gameId, 1))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }

    @Test
    void getRequired_returnsChainFactsAndMarksThemConsumedExactlyOnce() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        contextRepository.upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);

        var chainId = UUID.randomUUID();
        var chainFact = new ScoringContextChainFactJpaEntity();
        chainFact.setId(UUID.randomUUID());
        chainFact.setGameId(gameId);
        chainFact.setPlayerId(playerId);
        chainFact.setChainId(chainId);
        chainFact.setReason(ScoreReason.CHAIN_COMPLETED.name());
        chainFact.setEraNumber(5);
        chainFact.setConsumed(false);
        chainFactJpaRepository.save(chainFact);

        // getRequired is called for era 1, but the fact keeps its own era (5) — proves consuming a
        // fact during a later scoring pass doesn't misattribute it to that pass's era.
        var firstContext = contextRepository.getRequired(gameId, 1);
        assertThat(firstContext.chainFacts())
                .containsExactly(new ChainScoringFact(playerId, chainId, ScoreReason.CHAIN_COMPLETED, 5));

        var secondContext = contextRepository.getRequired(gameId, 2);
        assertThat(secondContext.chainFacts()).isEmpty();
    }
}
