package io.github.temporalrift.game.scoring.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.application.port.in.GetScoringHistoryUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository.ScoreHistoryRow;

@ExtendWith(MockitoExtension.class)
class GetScoringHistoryQueryHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();

    @Mock
    ScoringReadRepository scoringReadRepository;

    @InjectMocks
    GetScoringHistoryQueryHandler handler;

    @Test
    @DisplayName("history groups by ascending era and maps reason names")
    void handle_groupsByEra() {
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_1, 4, ScoreReason.EVENT_RESOLVED_AS_WRITTEN),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, -3, ScoreReason.CHAIN_BROKEN),
                        new ScoreHistoryRow(GAME_ID, 2, PLAYER_1, 8, ScoreReason.FULFILLMENT_SUCCEEDED)));

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID));

        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.history())
                .extracting(GetScoringHistoryUseCase.EraScoreHistory::eraNumber)
                .containsExactly(1, 2);

        var era1 = result.history().get(0);
        assertThat(era1.deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::reason)
                .containsExactly("EVENT_RESOLVED_AS_WRITTEN", "CHAIN_BROKEN");
        assertThat(era1.deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::pointsDelta)
                .containsExactly(4, -3);

        var era2 = result.history().get(1);
        assertThat(era2.deltas()).singleElement().satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_1);
            assertThat(delta.reason()).isEqualTo("FULFILLMENT_SUCCEEDED");
        });
    }

    @Test
    @DisplayName("no history persisted — throws ScoringGameNotFoundException")
    void handle_noHistory_throws() {
        given(scoringReadRepository.findScoreHistory(GAME_ID)).willReturn(List.of());
        var query = new GetScoringHistoryUseCase.Query(GAME_ID);

        assertThatExceptionOfType(ScoringGameNotFoundException.class).isThrownBy(() -> handler.handle(query));
    }
}
