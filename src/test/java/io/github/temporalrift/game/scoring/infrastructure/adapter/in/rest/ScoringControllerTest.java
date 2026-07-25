package io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.temporalrift.game.TestSecurityConfig;
import io.github.temporalrift.game.scoring.application.port.in.GetScoresUseCase;
import io.github.temporalrift.game.scoring.application.port.in.GetScoringHistoryUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.shared.PlayerPrincipal;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerAuthenticationToken;
import io.github.temporalrift.game.shared.infrastructure.config.SecurityConfig;

@WebMvcTest(ScoringController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class ScoringControllerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetScoresUseCase getScoresUseCase;

    @MockitoBean
    GetScoringHistoryUseCase getScoringHistoryUseCase;

    private RequestPostProcessor auth() {
        return authentication(new PlayerAuthenticationToken(new PlayerPrincipal(PLAYER_ID)));
    }

    @Test
    @DisplayName("Given no JWT, when GET scores, then 401")
    void getScoresNoJwt() throws Exception {
        mockMvc.perform(get("/games/{gameId}/scores", GAME_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given hidden factions, when GET scores, then 200 with playerName, score and null faction")
    void getScores() throws Exception {
        given(getScoresUseCase.handle(any()))
                .willReturn(new GetScoresUseCase.Result(
                        GAME_ID, 2, List.of(new GetScoresUseCase.PlayerScoreRow(PLAYER_ID, "Ada", 12, null))));

        mockMvc.perform(get("/games/{gameId}/scores", GAME_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.eraNumber").value(2))
                .andExpect(jsonPath("$.scores[0].playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.scores[0].playerName").value("Ada"))
                .andExpect(jsonPath("$.scores[0].score").value(12))
                .andExpect(jsonPath("$.scores[0].faction").doesNotExist());
    }

    @Test
    @DisplayName("Given grouped history, when GET scores/history, then 200 with era deltas")
    void getScoresHistory() throws Exception {
        given(getScoringHistoryUseCase.handle(any()))
                .willReturn(new GetScoringHistoryUseCase.Result(
                        GAME_ID,
                        List.of(new GetScoringHistoryUseCase.EraScoreHistory(
                                1,
                                List.of(new GetScoringHistoryUseCase.ScoreDeltaRow(
                                        PLAYER_ID, 4, "EVENT_RESOLVED_AS_WRITTEN"))))));

        mockMvc.perform(get("/games/{gameId}/scores/history", GAME_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.history[0].eraNumber").value(1))
                .andExpect(jsonPath("$.history[0].deltas[0].playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.history[0].deltas[0].pointsDelta").value(4))
                .andExpect(jsonPath("$.history[0].deltas[0].reason").value("EVENT_RESOLVED_AS_WRITTEN"));
    }

    @Test
    @DisplayName("Given ScoringGameNotFoundException, when GET scores, then 404")
    void getScoresNotFound() throws Exception {
        given(getScoresUseCase.handle(any())).willThrow(new ScoringGameNotFoundException(GAME_ID));

        mockMvc.perform(get("/games/{gameId}/scores", GAME_ID).with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404-01"));
    }
}
