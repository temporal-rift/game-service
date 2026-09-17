package io.github.temporalrift.game.scoring.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class FactionObjectiveQueryServiceTest {

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    VictoryRulesPort victoryRulesPort;

    @Test
    @DisplayName("evaluate maps every player score to its objective progress")
    void evaluate_mapsScoresToProgress() {
        var gameId = UUID.randomUUID();
        var eraser = new PlayerScore(UUID.randomUUID(), gameId, UUID.randomUUID(), Faction.ERASERS);
        for (int era = 1; era <= 4; era++) {
            eraser.apply(era, ScoreReason.ANNIHILATED_OUTCOME, 3);
        }
        var prophet = new PlayerScore(UUID.randomUUID(), gameId, UUID.randomUUID(), Faction.PROPHETS);
        prophet.apply(1, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
        given(playerScoreRepository.findAllByGameId(gameId)).willReturn(List.of(eraser, prophet));
        given(victoryRulesPort.eraserAnnihilations()).willReturn(4);
        given(victoryRulesPort.prophetWrittenResolutions()).willReturn(5);

        var service = new FactionObjectiveQueryService(playerScoreRepository, victoryRulesPort);

        var result = service.evaluate(gameId, 4);

        assertThat(result).hasSize(2);
        assertThat(result)
                .filteredOn(progress -> progress.playerId().equals(eraser.playerId()))
                .singleElement()
                .matches(progress -> progress.objectiveMet() && progress.progressCount() == 4);
        assertThat(result)
                .filteredOn(progress -> progress.playerId().equals(prophet.playerId()))
                .singleElement()
                .matches(progress -> !progress.objectiveMet());
    }
}
