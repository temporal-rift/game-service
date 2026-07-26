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

import io.github.temporalrift.game.scoring.application.port.in.GetScoresUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository.CurrentScoreRow;
import io.github.temporalrift.game.shared.Faction;

@ExtendWith(MockitoExtension.class)
class GetScoresQueryHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final UUID PLAYER_3 = UUID.randomUUID();

    @Mock
    ScoringReadRepository scoringReadRepository;

    @Mock
    ScoringGameVisibilityRepository visibilityRepository;

    @InjectMocks
    GetScoresQueryHandler handler;

    @Test
    @DisplayName("factions hidden — every score row returns faction null, in repository order")
    void handle_factionsHidden_returnsNullFactions() {
        given(scoringReadRepository.findCurrentScores(GAME_ID)).willReturn(rows());
        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoresUseCase.Query(GAME_ID));

        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.eraNumber()).isEqualTo(2);
        assertThat(result.scores())
                .extracting(GetScoresUseCase.PlayerScoreRow::playerId)
                .containsExactly(PLAYER_1, PLAYER_2, PLAYER_3);
        assertThat(result.scores()).allSatisfy(row -> assertThat(row.faction()).isNull());
        assertThat(result.scores())
                .extracting(GetScoresUseCase.PlayerScoreRow::playerName)
                .containsExactly("Ada", "Bo", "Cy");
    }

    @Test
    @DisplayName("factions revealed — each row carries its stored faction")
    void handle_factionsRevealed_returnsStoredFactions() {
        given(scoringReadRepository.findCurrentScores(GAME_ID)).willReturn(rows());
        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(true);

        var result = handler.handle(new GetScoresUseCase.Query(GAME_ID));

        assertThat(result.scores())
                .extracting(GetScoresUseCase.PlayerScoreRow::faction)
                .containsExactly(Faction.ERASERS, Faction.PROPHETS, Faction.WEAVERS);
    }

    @Test
    @DisplayName("no scores persisted — throws ScoringGameNotFoundException")
    void handle_noScores_throws() {
        given(scoringReadRepository.findCurrentScores(GAME_ID)).willReturn(List.of());
        var query = new GetScoresUseCase.Query(GAME_ID);

        assertThatExceptionOfType(ScoringGameNotFoundException.class).isThrownBy(() -> handler.handle(query));
    }

    private static List<CurrentScoreRow> rows() {
        return List.of(
                new CurrentScoreRow(GAME_ID, 2, PLAYER_1, "Ada", 12, Faction.ERASERS),
                new CurrentScoreRow(GAME_ID, 2, PLAYER_2, "Bo", 8, Faction.PROPHETS),
                new CurrentScoreRow(GAME_ID, 2, PLAYER_3, "Cy", 6, Faction.WEAVERS));
    }
}
