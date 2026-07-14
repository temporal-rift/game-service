package io.github.temporalrift.game.scoring.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;

@ExtendWith(MockitoExtension.class)
class PlayerScoreQueryServiceTest {

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @InjectMocks
    PlayerScoreQueryService queryService;

    @Test
    void getScores_mapsAndSortsByPlayerId() {
        var gameId = UUID.randomUUID();
        var laterPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var earlierPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var laterScore =
                PlayerScore.reconstitute(UUID.randomUUID(), gameId, laterPlayerId, Faction.WEAVERS, 10, List.of());
        var earlierScore =
                PlayerScore.reconstitute(UUID.randomUUID(), gameId, earlierPlayerId, Faction.ERASERS, -3, List.of());
        given(playerScoreRepository.findAllByGameId(gameId)).willReturn(List.of(laterScore, earlierScore));

        var results = queryService.getScores(gameId);

        assertThat(results).extracting(r -> r.playerId()).containsExactly(earlierPlayerId, laterPlayerId);
        assertThat(results.get(0).faction()).isEqualTo(Faction.ERASERS.name());
        assertThat(results.get(0).score()).isEqualTo(-3);
        assertThat(results.get(1).faction()).isEqualTo(Faction.WEAVERS.name());
        assertThat(results.get(1).score()).isEqualTo(10);
    }

    @Test
    void getScores_returnsEmptyListWhenNoScoresExist() {
        var gameId = UUID.randomUUID();
        given(playerScoreRepository.findAllByGameId(gameId)).willReturn(List.of());

        assertThat(queryService.getScores(gameId)).isEmpty();
    }

    @Test
    void getScores_rejectsNullGameId() {
        assertThatThrownBy(() -> queryService.getScores(null)).isInstanceOf(NullPointerException.class);
    }
}
