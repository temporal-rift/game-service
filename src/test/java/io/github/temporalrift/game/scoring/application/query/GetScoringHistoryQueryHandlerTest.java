package io.github.temporalrift.game.scoring.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository.ScoreHistoryRow;

@ExtendWith(MockitoExtension.class)
class GetScoringHistoryQueryHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();

    @Mock
    ScoringReadRepository scoringReadRepository;

    @Mock
    ScoringPlayerRepository scoringPlayerRepository;

    @Mock
    ScoringGameVisibilityRepository visibilityRepository;

    @InjectMocks
    GetScoringHistoryQueryHandler handler;

    @Test
    @DisplayName("before final reveal, history retains own reasons and omits opponent reasons")
    void handle_beforeFinalReveal_filtersOpponentReasons() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_1, 4, ScoreReason.EVENT_RESOLVED_AS_WRITTEN),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, -3, ScoreReason.CHAIN_BROKEN),
                        new ScoreHistoryRow(GAME_ID, 2, PLAYER_1, 8, ScoreReason.FULFILLMENT_SUCCEEDED)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.history())
                .extracting(GetScoringHistoryUseCase.EraScoreHistory::eraNumber)
                .containsExactly(1, 2);

        var era1 = result.history().get(0);
        assertThat(era1.deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::reason)
                .containsExactly("EVENT_RESOLVED_AS_WRITTEN", null);
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
    @DisplayName("after final reveal, history retains every recorded reason")
    void handle_afterFinalReveal_returnsAllReasons() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_1, 4, ScoreReason.EVENT_RESOLVED_AS_WRITTEN),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, -3, ScoreReason.CHAIN_BROKEN)));
        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(true);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.history().getFirst().deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::reason)
                .containsExactly("EVENT_RESOLVED_AS_WRITTEN", "CHAIN_BROKEN");
    }

    @Test
    @DisplayName("before final reveal, opponent rows in one era combine into a single net entry")
    void handle_beforeFinalReveal_combinesOpponentReasonsIntoNetEntry() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_1, 4, ScoreReason.EVENT_RESOLVED_AS_WRITTEN),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 2, ScoreReason.MIMIC_CONTRIBUTED_TO_WIN),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 4, ScoreReason.SECRET_OUTCOME_WON),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_1, 8, ScoreReason.FULFILLMENT_SUCCEEDED)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        var era1 = result.history().getFirst();
        // Own rows keep reason-level detail; the opponent's two reasons collapse to one net entry
        // with no reason, so neither the magnitudes (+2/+4) nor the reason count are disclosed.
        assertThat(era1.deltas()).hasSize(3);
        assertThat(era1.deltas().get(0)).satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_1);
            assertThat(delta.pointsDelta()).isEqualTo(4);
            assertThat(delta.reason()).isEqualTo("EVENT_RESOLVED_AS_WRITTEN");
        });
        assertThat(era1.deltas().get(1)).satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_2);
            assertThat(delta.pointsDelta()).isEqualTo(6);
            assertThat(delta.reason()).isNull();
        });
        assertThat(era1.deltas().get(2)).satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_1);
            assertThat(delta.pointsDelta()).isEqualTo(8);
            assertThat(delta.reason()).isEqualTo("FULFILLMENT_SUCCEEDED");
        });
    }

    @Test
    @DisplayName("before final reveal, unique award magnitudes do not disclose the opponent reason")
    void handle_beforeFinalReveal_uniqueMagnitudesDoNotDiscloseReason() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        // Two uniquely Eraser-sized awards in one era: must not appear as +3/+5 rows.
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 3, ScoreReason.ANNIHILATED_OUTCOME),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 5, ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES),
                        // Uniquely Weaver-sized award on its own in another era: net equals the magnitude
                        // (the same total-score information already public via current scores) but carries
                        // no reason and no confirmation that exactly one reason produced it.
                        new ScoreHistoryRow(GAME_ID, 2, PLAYER_2, 10, ScoreReason.CHAIN_COMPLETED)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.history())
                .extracting(GetScoringHistoryUseCase.EraScoreHistory::eraNumber)
                .containsExactly(1, 2);
        assertThat(result.history().get(0).deltas()).singleElement().satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_2);
            assertThat(delta.pointsDelta()).isEqualTo(8);
            assertThat(delta.reason()).isNull();
        });
        assertThat(result.history().get(1).deltas()).singleElement().satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_2);
            assertThat(delta.pointsDelta()).isEqualTo(10);
            assertThat(delta.reason()).isNull();
        });
    }

    @Test
    @DisplayName("before final reveal, each opponent keeps a separate net entry per era")
    void handle_beforeFinalReveal_keepsOpponentsSeparate() {
        var player3 = UUID.randomUUID();
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 3, ScoreReason.ANNIHILATED_OUTCOME),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 2, ScoreReason.CORRUPTED_OPPONENT_CARD),
                        new ScoreHistoryRow(GAME_ID, 1, player3, 4, ScoreReason.SECRET_OUTCOME_WON)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.history().getFirst().deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::playerId)
                .containsExactly(PLAYER_2, player3);
        assertThat(result.history().getFirst().deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::pointsDelta)
                .containsExactly(5, 4);
        assertThat(result.history().getFirst().deltas())
                .extracting(GetScoringHistoryUseCase.ScoreDeltaRow::reason)
                .containsExactly(null, null);
    }

    @Test
    @DisplayName("before final reveal, the end-game entry follows the same opponent-net rule")
    void handle_beforeFinalReveal_endGameEntryFollowsOpponentNetRule() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 0, PLAYER_2, 6, ScoreReason.FACTION_UNIDENTIFIED),
                        new ScoreHistoryRow(GAME_ID, 0, PLAYER_1, 6, ScoreReason.FACTION_UNIDENTIFIED)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(false);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.history()).singleElement().satisfies(era -> {
            assertThat(era.eraNumber()).isZero();
            assertThat(era.deltas()).hasSize(2);
            assertThat(era.deltas().get(0)).satisfies(delta -> {
                assertThat(delta.playerId()).isEqualTo(PLAYER_2);
                assertThat(delta.pointsDelta()).isEqualTo(6);
                assertThat(delta.reason()).isNull();
            });
            assertThat(era.deltas().get(1)).satisfies(delta -> {
                assertThat(delta.playerId()).isEqualTo(PLAYER_1);
                assertThat(delta.pointsDelta()).isEqualTo(6);
                assertThat(delta.reason()).isEqualTo("FACTION_UNIDENTIFIED");
            });
        });
    }

    @Test
    @DisplayName("after final reveal, the end-game entry remains fully auditable")
    void handle_afterFinalReveal_endGameEntryAuditable() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID))
                .willReturn(List.of(
                        new ScoreHistoryRow(GAME_ID, 0, PLAYER_2, 6, ScoreReason.FACTION_UNIDENTIFIED),
                        new ScoreHistoryRow(GAME_ID, 1, PLAYER_2, 3, ScoreReason.ANNIHILATED_OUTCOME)));

        given(visibilityRepository.areFactionsRevealed(GAME_ID)).willReturn(true);

        var result = handler.handle(new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1));

        assertThat(result.history())
                .extracting(GetScoringHistoryUseCase.EraScoreHistory::eraNumber)
                .containsExactly(0, 1);
        assertThat(result.history().get(0).deltas()).singleElement().satisfies(delta -> {
            assertThat(delta.playerId()).isEqualTo(PLAYER_2);
            assertThat(delta.pointsDelta()).isEqualTo(6);
            assertThat(delta.reason()).isEqualTo("FACTION_UNIDENTIFIED");
        });
        assertThat(result.history().get(1).deltas()).singleElement().satisfies(delta -> {
            assertThat(delta.reason()).isEqualTo("ANNIHILATED_OUTCOME");
        });
    }

    @Test
    @DisplayName("no history persisted — throws ScoringGameNotFoundException")
    void handle_noHistory_throws() {
        authorize(PLAYER_1);
        given(scoringReadRepository.findScoreHistory(GAME_ID)).willReturn(List.of());
        var query = new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_1);

        assertThatExceptionOfType(ScoringGameNotFoundException.class).isThrownBy(() -> handler.handle(query));
    }

    @Test
    @DisplayName("non-participant — returns the same not-found denial without reading history")
    void handle_nonParticipant_throwsWithoutReadingHistory() {
        given(scoringPlayerRepository.isParticipant(GAME_ID, PLAYER_2)).willReturn(false);
        var query = new GetScoringHistoryUseCase.Query(GAME_ID, PLAYER_2);

        assertThatExceptionOfType(ScoringGameNotFoundException.class).isThrownBy(() -> handler.handle(query));

        then(scoringReadRepository).should(never()).findScoreHistory(GAME_ID);
    }

    private void authorize(UUID playerId) {
        given(scoringPlayerRepository.isParticipant(GAME_ID, playerId)).willReturn(true);
    }
}
