package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.temporalrift.game.TestSecurityConfig;
import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.shared.PlayerPrincipal;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerAuthenticationToken;
import io.github.temporalrift.game.shared.infrastructure.config.SecurityConfig;

@WebMvcTest(ActionController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class ActionControllerTest {

    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID CARD_INSTANCE_ID = UUID.randomUUID();
    static final UUID TARGET_EVENT_ID = UUID.randomUUID();
    static final UUID SOURCE_OUTCOME_ID = UUID.randomUUID();
    static final UUID TARGET_OUTCOME_ID = UUID.randomUUID();
    static final UUID TARGET_PLAYER_ID = UUID.randomUUID();
    static final int ERA = 2;
    static final int ROUND = 3;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlayCardUseCase playCardUseCase;

    @MockitoBean
    PlaySpecialActionUseCase playSpecialActionUseCase;

    @MockitoBean
    GetRoundStatusUseCase getRoundStatusUseCase;

    private RequestPostProcessor auth() {
        return authentication(new PlayerAuthenticationToken(new PlayerPrincipal(PLAYER_ID)));
    }

    @Test
    @DisplayName("Given no JWT, when POST action, then 401")
    void submitActionNoJwt() throws Exception {
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given CARD request, when POST action, then dispatches authenticated command and returns 202")
    void submitCard() throws Exception {
        // given
        given(playCardUseCase.handle(any()))
                .willReturn(new PlayCardUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.eraNumber").value(ERA))
                .andExpect(jsonPath("$.roundNumber").value(ROUND))
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.roundClosed").value(false));

        var captor = ArgumentCaptor.forClass(PlayCardUseCase.Command.class);
        org.mockito.BDDMockito.then(playCardUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().roundNumber()).isEqualTo(ROUND);
        assertThat(captor.getValue().playerId()).isEqualTo(PLAYER_ID);
        assertThat(captor.getValue().cardInstanceId()).isEqualTo(CARD_INSTANCE_ID);
        assertThat(captor.getValue().targetEventId()).isEqualTo(TARGET_EVENT_ID);
        assertThat(captor.getValue().sourceOutcomeId()).isEqualTo(SOURCE_OUTCOME_ID);
        assertThat(captor.getValue().targetOutcomeId()).isEqualTo(TARGET_OUTCOME_ID);
    }

    @Test
    @DisplayName("Given SPECIAL request, when POST action, then maps generated enum and returns 202")
    void submitSpecial() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any()))
                .willReturn(new PlaySpecialActionUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, true));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.roundClosed").value(true));

        var captor = ArgumentCaptor.forClass(PlaySpecialActionUseCase.Command.class);
        org.mockito.BDDMockito.then(playSpecialActionUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().roundNumber()).isEqualTo(ROUND);
        assertThat(captor.getValue().playerId()).isEqualTo(PLAYER_ID);
        assertThat(captor.getValue().specialAction())
                .isEqualTo(io.github.temporalrift.events.shared.SpecialAction.CORRUPT);
        assertThat(captor.getValue().targetEventId()).isEqualTo(TARGET_EVENT_ID);
        assertThat(captor.getValue().targetOutcomeId()).isEqualTo(TARGET_OUTCOME_ID);
        assertThat(captor.getValue().targetPlayerId()).isEqualTo(TARGET_PLAYER_ID);
    }

    @Test
    @DisplayName("Given no JWT, when GET status, then 401")
    void getRoundStatusNoJwt() throws Exception {
        mockMvc.perform(get("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/status", GAME_ID, ERA, ROUND))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given round status, when GET status, then returns public status")
    void getRoundStatus() throws Exception {
        // given
        var pendingPlayerId = UUID.randomUUID();
        given(getRoundStatusUseCase.handle(any()))
                .willReturn(new GetRoundStatusUseCase.Result(ERA, ROUND, "OPEN", 42, 2, 3, List.of(pendingPlayerId)));

        // when / then
        mockMvc.perform(get("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/status", GAME_ID, ERA, ROUND)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eraNumber").value(ERA))
                .andExpect(jsonPath("$.roundNumber").value(ROUND))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.timerRemainingSeconds").value(42))
                .andExpect(jsonPath("$.submittedCount").value(2))
                .andExpect(jsonPath("$.totalPlayers").value(3))
                .andExpect(jsonPath("$.pendingPlayerIds[0]").value(pendingPlayerId.toString()));
    }

    @Test
    @DisplayName("Given RoundNotFoundException, then returns 404")
    void roundNotFound() throws Exception {
        // given
        given(getRoundStatusUseCase.handle(any())).willThrow(new RoundNotFoundException(GAME_ID, ERA, ROUND));

        // when / then
        mockMvc.perform(get("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/status", GAME_ID, ERA, ROUND)
                        .with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404-01"));
    }

    @Test
    @DisplayName("Given ActionRoundClosedException, then returns 409")
    void actionRoundClosed() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new ActionRoundClosedException());

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-01"));
    }

    @Test
    @DisplayName("Given DuplicateSubmissionException, then returns 409")
    void duplicateSubmission() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new DuplicateSubmissionException(PLAYER_ID));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-02"));
    }

    @Test
    @DisplayName("Given CardNotInHandException, then returns 422")
    void cardNotInHand() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new CardNotInHandException(CARD_INSTANCE_ID));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-01"));
    }

    @Test
    @DisplayName("Given JammedPlayerException, then returns 422")
    void jammedPlayer() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any())).willThrow(new JammedPlayerException(PLAYER_ID));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-02"));
    }

    @Test
    @DisplayName("Given FactionRequiredException, then returns 422")
    void factionRequired() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any())).willThrow(new FactionRequiredException(PLAYER_ID));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-04"));
    }

    @Test
    @DisplayName("Given InvalidActionTargetException, then returns 422")
    void invalidActionTarget() throws Exception {
        // given
        given(playCardUseCase.handle(any()))
                .willThrow(new InvalidActionTargetException(
                        io.github.temporalrift.events.shared.CardType.SWING, SOURCE_OUTCOME_ID, SOURCE_OUTCOME_ID));

        // when / then
        mockMvc.perform(post("/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-03"));
    }

    private static String cardJson() {
        return """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetEventId": "%s",
                  "sourceOutcomeId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(CARD_INSTANCE_ID, TARGET_EVENT_ID, SOURCE_OUTCOME_ID, TARGET_OUTCOME_ID);
    }

    private static String specialJson() {
        return """
                {
                  "actionType": "SPECIAL",
                  "specialAction": "CORRUPT",
                  "targetEventId": "%s",
                  "targetOutcomeId": "%s",
                  "targetPlayerId": "%s"
                }
                """.formatted(TARGET_EVENT_ID, TARGET_OUTCOME_ID, TARGET_PLAYER_ID);
    }
}
