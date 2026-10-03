package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import io.github.temporalrift.game.action.application.port.in.PassActionRoundUseCase;
import io.github.temporalrift.game.action.application.port.in.PassParadoxResolutionUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.SelectHandUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.CardNotEligibleForRoundException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionNotEligibleForEraException;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionNotEligibleForRoundException;
import io.github.temporalrift.game.action.domain.activisterastate.ExposeAlreadyRecordedException;
import io.github.temporalrift.game.action.domain.handselection.InvalidHandSelectionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.CardNotEligibleForParadoxResolutionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.DuplicateParadoxResolutionSubmissionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraBudgetExhaustedException;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerAuthenticationToken;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerPrincipal;
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
    static final String PASS_JSON = "{\"actionType\": \"PASS\"}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlayCardUseCase playCardUseCase;

    @MockitoBean
    PlaySpecialActionUseCase playSpecialActionUseCase;

    @MockitoBean
    PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase;

    @MockitoBean
    PassActionRoundUseCase passActionRoundUseCase;

    @MockitoBean
    PassParadoxResolutionUseCase passParadoxResolutionUseCase;

    @MockitoBean
    RecordActivistDeclarationUseCase recordActivistDeclarationUseCase;

    @MockitoBean
    SelectHandUseCase selectHandUseCase;

    @MockitoBean
    io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase declineDeclarationUseCase;

    private RequestPostProcessor auth() {
        return authentication(new PlayerAuthenticationToken(new PlayerPrincipal(PLAYER_ID)));
    }

    @Test
    void declineRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/declarations/decline", GAME_ID, ERA))
                .andExpect(status().isUnauthorized());
        then(declineDeclarationUseCase).shouldHaveNoInteractions();
    }

    @Test
    void declineUsesAuthenticatedIdentityAndAcknowledgesTerminalDecision() throws Exception {
        given(declineDeclarationUseCase.handle(any()))
                .willReturn(new io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase.Result(
                        GAME_ID, ERA, PLAYER_ID));
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/declarations/decline", GAME_ID, ERA)
                        .with(auth()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.status").value("DECLINED"));
        then(declineDeclarationUseCase)
                .should()
                .handle(new io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase.Command(
                        GAME_ID, ERA, PLAYER_ID));
    }

    @Test
    void declineCannotReplaceDeclaration() throws Exception {
        given(declineDeclarationUseCase.handle(any()))
                .willThrow(
                        new io.github.temporalrift.game.action.domain.declarationphase
                                .DeclarationAlreadyDecidedException(PLAYER_ID, ERA));
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/declarations/decline", GAME_ID, ERA)
                        .with(auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-04"));
    }

    @Test
    void submitParadoxResolutionCardRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paradoxResolutionJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitParadoxResolutionCardDispatchesAuthenticatedCommand() throws Exception {
        given(playParadoxResolutionCardUseCase.handle(any()))
                .willReturn(new PlayParadoxResolutionCardUseCase.Result(GAME_ID, ERA, PLAYER_ID));

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paradoxResolutionJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.eraNumber").value(ERA))
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        var captor = ArgumentCaptor.forClass(PlayParadoxResolutionCardUseCase.Command.class);
        org.mockito.BDDMockito.then(playParadoxResolutionCardUseCase).should().handle(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new PlayParadoxResolutionCardUseCase.Command(
                        GAME_ID, ERA, PLAYER_ID, CARD_INSTANCE_ID, TARGET_EVENT_ID, TARGET_OUTCOME_ID));
    }

    @Test
    void paradoxResolutionErrorsUsePublishedStableCodes() throws Exception {
        var endpoint = post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(paradoxResolutionJson());
        willThrow(new ParadoxResolutionPhaseNotOpenException(GAME_ID, ERA))
                .given(playParadoxResolutionCardUseCase)
                .handle(any());
        mockMvc.perform(endpoint)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-06"));

        willThrow(new DuplicateParadoxResolutionSubmissionException(PLAYER_ID))
                .given(playParadoxResolutionCardUseCase)
                .handle(any());
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paradoxResolutionJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-07"));

        willThrow(new CardNotEligibleForParadoxResolutionException(
                        io.github.temporalrift.game.shared.domain.model.CardType.COLLIDE))
                .given(playParadoxResolutionCardUseCase)
                .handle(any());
        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paradoxResolutionJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-10"));
    }

    @Test
    @DisplayName("Given an explicit paradox-resolution pass, when POST, then dispatches the pass and spends no card")
    void submitParadoxResolutionPassDispatchesPassCommand() throws Exception {
        given(passParadoxResolutionUseCase.handle(any()))
                .willReturn(new PassParadoxResolutionUseCase.Result(GAME_ID, ERA, PLAYER_ID));

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASS_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.eraNumber").value(ERA))
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        then(passParadoxResolutionUseCase)
                .should()
                .handle(new PassParadoxResolutionUseCase.Command(GAME_ID, ERA, PLAYER_ID));
        then(playParadoxResolutionCardUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given an explicit CARD paradox-resolution submission, when POST, then dispatches the card")
    void submitParadoxResolutionExplicitCardDispatchesCardCommand() throws Exception {
        given(playParadoxResolutionCardUseCase.handle(any()))
                .willReturn(new PlayParadoxResolutionCardUseCase.Result(GAME_ID, ERA, PLAYER_ID));

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paradoxResolutionJson("CARD")))
                .andExpect(status().isAccepted());

        then(playParadoxResolutionCardUseCase)
                .should()
                .handle(new PlayParadoxResolutionCardUseCase.Command(
                        GAME_ID, ERA, PLAYER_ID, CARD_INSTANCE_ID, TARGET_EVENT_ID, TARGET_OUTCOME_ID));
        then(passParadoxResolutionUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given a paradox-resolution document that does not match its actionType, when POST, then 400")
    void paradoxResolutionRejectsPartialDocuments() throws Exception {
        var passWithCard = paradoxResolutionJson("PASS");
        var cardWithoutTarget = """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s"
                }
                """.formatted(CARD_INSTANCE_ID);
        var omittedTypeWithoutCard = """
                {
                  "targetEventId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(TARGET_EVENT_ID, TARGET_OUTCOME_ID);
        var special = "{\"actionType\": \"SPECIAL\"}";

        for (var body : List.of(passWithCard, cardWithoutTarget, omittedTypeWithoutCard, special)) {
            mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400-01"));
        }
        then(playParadoxResolutionCardUseCase).shouldHaveNoInteractions();
        then(passParadoxResolutionUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given a player already submitted or passed, when they pass paradox resolution, then 409-07")
    void paradoxResolutionPassAfterSubmissionIsRejected() throws Exception {
        willThrow(new DuplicateParadoxResolutionSubmissionException(PLAYER_ID))
                .given(passParadoxResolutionUseCase)
                .handle(any());

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/paradox-resolution/actions", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASS_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-07"));
    }

    @Test
    @DisplayName("Given PASS request, when POST action, then dispatches the pass and reports the early close")
    void submitPass() throws Exception {
        given(passActionRoundUseCase.handle(any()))
                .willReturn(new PassActionRoundUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, true));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASS_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.eraNumber").value(ERA))
                .andExpect(jsonPath("$.roundNumber").value(ROUND))
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.roundClosed").value(true));

        then(passActionRoundUseCase)
                .should()
                .handle(new PassActionRoundUseCase.Command(GAME_ID, ERA, ROUND, PLAYER_ID));
        then(playCardUseCase).shouldHaveNoInteractions();
        then(playSpecialActionUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given a player already submitted or the round closed, when they pass, then the usual 409 codes")
    void passConflictsUseSubmissionCodes() throws Exception {
        var endpoint = post("/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions", GAME_ID, ERA, ROUND)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PASS_JSON);

        willThrow(new DuplicateSubmissionException(PLAYER_ID))
                .given(passActionRoundUseCase)
                .handle(any());
        mockMvc.perform(endpoint)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-02"));

        willThrow(new ActionRoundClosedException())
                .given(passActionRoundUseCase)
                .handle(any());
        mockMvc.perform(endpoint)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-01"));
    }

    @Test
    @DisplayName("Given no JWT, when POST action, then 401")
    void submitActionNoJwt() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
        assertThat(captor.getValue().targetEventIds()).isNull();
        assertThat(captor.getValue().sourceOutcomeId()).isEqualTo(SOURCE_OUTCOME_ID);
        assertThat(captor.getValue().targetOutcomeId()).isEqualTo(TARGET_OUTCOME_ID);
    }

    @Test
    @DisplayName("Given SCAN list request, when POST action, then preserves every selected event id")
    void submitScanTargets() throws Exception {
        var secondEventId = UUID.randomUUID();
        given(playCardUseCase.handle(any()))
                .willReturn(new PlayCardUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(secondEventId)))
                .andExpect(status().isAccepted());

        var captor = ArgumentCaptor.forClass(PlayCardUseCase.Command.class);
        org.mockito.BDDMockito.then(playCardUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().targetEventId()).isNull();
        assertThat(captor.getValue().targetEventIds()).containsExactly(TARGET_EVENT_ID, secondEventId);
        assertThat(captor.getValue().targetPlayerId()).isNull();
    }

    @Test
    @DisplayName("Given duplicate SCAN target JSON, when POST action, then rejects it before set deserialization")
    void submitScanDuplicateTargets() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(TARGET_EVENT_ID)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422-03"));

        org.mockito.BDDMockito.then(playCardUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given NULLIFY list request, when POST action, then preserves every selected player id")
    void submitNullifyTargets() throws Exception {
        var secondTargetId = UUID.randomUUID();
        given(playCardUseCase.handle(any()))
                .willReturn(new PlayCardUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullifyJson(secondTargetId)))
                .andExpect(status().isAccepted());

        var captor = ArgumentCaptor.forClass(PlayCardUseCase.Command.class);
        org.mockito.BDDMockito.then(playCardUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().targetEventId()).isNull();
        assertThat(captor.getValue().targetEventIds()).isNull();
        assertThat(captor.getValue().targetPlayerId()).isNull();
        assertThat(captor.getValue().targetPlayerIds()).containsExactly(TARGET_PLAYER_ID, secondTargetId);
    }

    @Test
    @DisplayName("Given DECOY disguise request, when POST action, then passes the disguise and no target")
    void submitDecoyDisguise() throws Exception {
        given(playCardUseCase.handle(any()))
                .willReturn(new PlayCardUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "CARD",
                                  "cardInstanceId": "%s",
                                  "disguiseCategory": "DISRUPTION"
                                }
                                """.formatted(CARD_INSTANCE_ID)))
                .andExpect(status().isAccepted());

        var captor = ArgumentCaptor.forClass(PlayCardUseCase.Command.class);
        org.mockito.BDDMockito.then(playCardUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().disguiseCategory()).isEqualTo(CardCategory.DISRUPTION);
        assertThat(captor.getValue().targetEventId()).isNull();
        assertThat(captor.getValue().targetPlayerId()).isNull();
    }

    @Test
    @DisplayName("Given duplicate NULLIFY target JSON, when POST action, then rejects it before set deserialization")
    void submitNullifyDuplicateTargets() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullifyJson(TARGET_PLAYER_ID)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422-03"));

        org.mockito.BDDMockito.then(playCardUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given SPECIAL request, when POST action, then maps generated enum and returns 202")
    void submitSpecial() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any()))
                .willReturn(new PlaySpecialActionUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, true));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
                .isEqualTo(io.github.temporalrift.game.shared.domain.model.SpecialAction.CORRUPT);
        assertThat(captor.getValue().targetEventId()).isNull();
        assertThat(captor.getValue().targetOutcomeId()).isNull();
        assertThat(captor.getValue().targetPlayerId()).isEqualTo(TARGET_PLAYER_ID);
    }

    @Test
    @DisplayName("Given RoundNotFoundException, then returns 404")
    void roundNotFound() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new RoundNotFoundException(GAME_ID, ERA, ROUND));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404-01"));
    }

    @Test
    @DisplayName("Given ActionRoundClosedException, then returns 409")
    void actionRoundClosed() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new ActionRoundClosedException());

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-01"));
    }

    @Test
    @DisplayName("Given CardNotEligibleForRoundException, then returns 422 with its own code, distinct from 422-10")
    void cardNotEligibleForRound() throws Exception {
        // given
        given(playCardUseCase.handle(any()))
                .willThrow(new CardNotEligibleForRoundException(CardType.TRACE, ERA, ROUND));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-12"));
    }

    @Test
    @DisplayName("Given final-era STALL rejection, then returns 422-12")
    void finalEraStallReturnsRoundIneligibilityCode() throws Exception {
        // given
        given(playCardUseCase.handle(any())).willThrow(new CardNotEligibleForRoundException(CardType.STALL, 5, 1));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-12"));
    }

    @Test
    @DisplayName("Given round-3 OBSCURE rejection, then returns 422-12")
    void roundThreeObscureReturnsRoundIneligibilityCode() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any()))
                .willThrow(new SpecialActionNotEligibleForRoundException(SpecialAction.OBSCURE, ERA, 3));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-12"));
    }

    @Test
    @DisplayName("Given final-era CASCADE rejection, then returns 422-12")
    void finalEraCascadeReturnsRoundIneligibilityCode() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any()))
                .willThrow(new SpecialActionNotEligibleForEraException(SpecialAction.CASCADE, 5));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-12"));
    }

    @Test
    @DisplayName("Given JammedPlayerException, then returns 422")
    void jammedPlayer() throws Exception {
        // given
        given(playSpecialActionUseCase.handle(any())).willThrow(new JammedPlayerException(PLAYER_ID));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
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
                .willThrow(InvalidActionTargetException.requiresDistinctOutcomes(CardType.SWING));

        // when / then
        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("422-03"));
    }

    @Test
    void freshTraceTargetExplainsPrecedingRoundRule() throws Exception {
        given(playCardUseCase.handle(any())).willThrow(InvalidActionTargetException.traceRequiresPrecedingRoundEvent());

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422-03"))
                .andExpect(jsonPath("$.detail")
                        .value("TRACE target event must have been active in the preceding action round"));
    }

    @Test
    @DisplayName("Given a prior Expose, then submits a 409 conflict")
    void exposeAlreadyRecorded() throws Exception {
        given(playSpecialActionUseCase.handle(any())).willThrow(new ExposeAlreadyRecordedException());

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-05"));
    }

    @Test
    @DisplayName("Given a budgeted special already used this era, then submits a 409 conflict distinguishable from "
            + "Expose's")
    void specialActionEraBudgetExhausted() throws Exception {
        given(playSpecialActionUseCase.handle(any()))
                .willThrow(new SpecialActionEraBudgetExhaustedException(PLAYER_ID, SpecialAction.ANNIHILATE, ERA));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-10"));
    }

    @Test
    @DisplayName("Given Seal game budget exhausted, then submits a 409 conflict without disclosing private state")
    void sealGameBudgetExhausted() throws Exception {
        given(playSpecialActionUseCase.handle(any()))
                .willThrow(
                        new io.github.temporalrift.game.action.domain.specialactionerausage
                                .SealGameBudgetExhaustedException(PLAYER_ID, 2));

        mockMvc.perform(post(
                                "/api/v1/games/{gameId}/eras/{eraNumber}/rounds/{roundNumber}/actions",
                                GAME_ID,
                                ERA,
                                ROUND)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specialJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409-10"));
    }

    @Test
    @DisplayName("Given a declaration request, when POSTed, then dispatches it and returns 202")
    void recordActivistDeclaration() throws Exception {
        given(recordActivistDeclarationUseCase.handle(any()))
                .willReturn(new RecordActivistDeclarationUseCase.Result(
                        GAME_ID,
                        ERA,
                        PLAYER_ID,
                        io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode.RALLY,
                        TARGET_EVENT_ID,
                        TARGET_OUTCOME_ID));

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/declarations", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declarationJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.playerId").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.specialAction").value("RALLY"))
                .andExpect(jsonPath("$.status").value("DECLARED"));

        var captor = ArgumentCaptor.forClass(RecordActivistDeclarationUseCase.Command.class);
        org.mockito.BDDMockito.then(recordActivistDeclarationUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().playerId()).isEqualTo(PLAYER_ID);
        assertThat(captor.getValue().mode())
                .isEqualTo(io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode.RALLY);
    }

    @Test
    void selectHand_dispatchesAuthenticatedFiveCardSelection() throws Exception {
        var keptCards = java.util.stream.IntStream.range(0, 5)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toSet());
        given(selectHandUseCase.handle(any())).willReturn(new SelectHandUseCase.Result(GAME_ID, ERA, PLAYER_ID));

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/hand-selection", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handSelectionJson(keptCards)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SELECTED"));

        var captor = ArgumentCaptor.forClass(SelectHandUseCase.Command.class);
        org.mockito.BDDMockito.then(selectHandUseCase).should().handle(captor.capture());
        assertThat(captor.getValue().playerId()).isEqualTo(PLAYER_ID);
        assertThat(captor.getValue().keptCardInstanceIds()).isEqualTo(keptCards);
    }

    @Test
    void selectHand_rejectsCardsOutsidePendingDeal() throws Exception {
        given(selectHandUseCase.handle(any())).willThrow(new InvalidHandSelectionException());
        var keptCards = java.util.stream.IntStream.range(0, 5)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toSet());

        mockMvc.perform(post("/api/v1/games/{gameId}/eras/{eraNumber}/hand-selection", GAME_ID, ERA)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handSelectionJson(keptCards)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422-11"));
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
                  "targetPlayerId": "%s"
                }
                """.formatted(TARGET_PLAYER_ID);
    }

    private static String scanJson(UUID secondEventId) {
        return """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetEventIds": ["%s", "%s"]
                }
                """.formatted(CARD_INSTANCE_ID, TARGET_EVENT_ID, secondEventId);
    }

    private static String nullifyJson(UUID secondTargetId) {
        return """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetPlayerIds": ["%s", "%s"]
                }
                """.formatted(CARD_INSTANCE_ID, TARGET_PLAYER_ID, secondTargetId);
    }

    private static String declarationJson() {
        return """
                {
                  "specialAction": "RALLY",
                  "targetEventId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(TARGET_EVENT_ID, TARGET_OUTCOME_ID);
    }

    private static String paradoxResolutionJson() {
        return """
                {
                  "cardInstanceId": "%s",
                  "targetEventId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(CARD_INSTANCE_ID, TARGET_EVENT_ID, TARGET_OUTCOME_ID);
    }

    private static String paradoxResolutionJson(String actionType) {
        return """
                {
                  "actionType": "%s",
                  "cardInstanceId": "%s",
                  "targetEventId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(actionType, CARD_INSTANCE_ID, TARGET_EVENT_ID, TARGET_OUTCOME_ID);
    }

    private static String handSelectionJson(java.util.Set<UUID> keptCardInstanceIds) {
        return "{\"keptCardInstanceIds\":[%s]}"
                .formatted(keptCardInstanceIds.stream()
                        .map(id -> "\"" + id + "\"")
                        .collect(java.util.stream.Collectors.joining(",")));
    }
}
