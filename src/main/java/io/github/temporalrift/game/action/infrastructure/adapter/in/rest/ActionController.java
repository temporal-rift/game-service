package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.SelectHandUseCase;
import io.github.temporalrift.game.action.application.port.in.SubmitParadoxResolutionUseCase;
import io.github.temporalrift.game.action.application.port.in.SubmitRoundActionUseCase;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.ActionApi;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActionSubmissionStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActionType;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.CardActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.DeclarationDeclineResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ParadoxResolutionCardRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ParadoxResolutionCardResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SpecialActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SubmitActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SubmitActionResponse;
import io.github.temporalrift.game.shared.infrastructure.config.CurrentPlayer;

@RestController
class ActionController implements ActionApi {

    private final DeclineDeclarationUseCase declineDeclarationUseCase;

    private final RecordActivistDeclarationUseCase recordActivistDeclarationUseCase;

    private final SelectHandUseCase selectHandUseCase;

    private final SubmitRoundActionUseCase submitRoundActionUseCase;

    private final SubmitParadoxResolutionUseCase submitParadoxResolutionUseCase;

    ActionController(
            DeclineDeclarationUseCase declineDeclarationUseCase,
            RecordActivistDeclarationUseCase recordActivistDeclarationUseCase,
            SelectHandUseCase selectHandUseCase,
            SubmitRoundActionUseCase submitRoundActionUseCase,
            SubmitParadoxResolutionUseCase submitParadoxResolutionUseCase) {
        this.declineDeclarationUseCase = declineDeclarationUseCase;
        this.recordActivistDeclarationUseCase = recordActivistDeclarationUseCase;
        this.selectHandUseCase = selectHandUseCase;
        this.submitRoundActionUseCase = submitRoundActionUseCase;
        this.submitParadoxResolutionUseCase = submitParadoxResolutionUseCase;
    }

    @Override
    public ResponseEntity<DeclarationDeclineResponse> declineDeclaration(UUID gameId, Integer eraNumber) {
        var result = declineDeclarationUseCase.handle(
                new DeclineDeclarationUseCase.Command(gameId, eraNumber, CurrentPlayer.id()));
        return ResponseEntity.accepted()
                .body(new DeclarationDeclineResponse(
                        result.gameId(),
                        result.eraNumber(),
                        result.playerId(),
                        DeclarationDeclineResponse.StatusEnum.DECLINED));
    }

    @Override
    public ResponseEntity<HandSelectionResponse> selectHand(
            UUID gameId, Integer eraNumber, HandSelectionRequest handSelectionRequest) {
        var result = selectHandUseCase.handle(new SelectHandUseCase.Command(
                gameId, eraNumber, CurrentPlayer.id(), handSelectionRequest.getKeptCardInstanceIds()));
        return ResponseEntity.accepted()
                .body(new HandSelectionResponse(
                        result.gameId(), result.eraNumber(), result.playerId(), HandSelectionStatus.SELECTED));
    }

    @Override
    public ResponseEntity<ParadoxResolutionCardResponse> submitParadoxResolutionCard(
            UUID gameId, Integer eraNumber, ParadoxResolutionCardRequest request) {
        var playerId = CurrentPlayer.id();
        // The contract keeps actionType optional so card clients that omit it stay compatible.
        var actionType = request.getActionType() == null ? ActionType.CARD : request.getActionType();
        SubmitParadoxResolutionUseCase.Command command = switch (actionType) {
            case CARD -> paradoxResolutionCard(gameId, eraNumber, playerId, request);
            case PASS -> paradoxResolutionPass(gameId, eraNumber, playerId, request);
            case SPECIAL -> throw InvalidParadoxResolutionRequestException.specialNotEligible();
        };
        var result = submitParadoxResolutionUseCase.handle(command);
        return ResponseEntity.accepted()
                .body(new ParadoxResolutionCardResponse(
                        result.gameId(), result.eraNumber(), result.playerId(), ActionSubmissionStatus.SUBMITTED));
    }

    private static SubmitParadoxResolutionUseCase.PlayCard paradoxResolutionCard(
            UUID gameId, int eraNumber, UUID playerId, ParadoxResolutionCardRequest request) {
        if (request.getCardInstanceId() == null
                || request.getTargetEventId() == null
                || request.getTargetOutcomeId() == null) {
            throw InvalidParadoxResolutionRequestException.cardRequiresAllFields();
        }
        return new SubmitParadoxResolutionUseCase.PlayCard(
                gameId,
                eraNumber,
                playerId,
                request.getCardInstanceId(),
                request.getTargetEventId(),
                request.getTargetOutcomeId());
    }

    private static SubmitParadoxResolutionUseCase.Pass paradoxResolutionPass(
            UUID gameId, int eraNumber, UUID playerId, ParadoxResolutionCardRequest request) {
        if (request.getCardInstanceId() != null
                || request.getTargetEventId() != null
                || request.getTargetOutcomeId() != null) {
            throw InvalidParadoxResolutionRequestException.passCarriesCardFields();
        }
        return new SubmitParadoxResolutionUseCase.Pass(gameId, eraNumber, playerId);
    }

    @Override
    public ResponseEntity<ActivistDeclarationResponse> recordActivistDeclaration(
            UUID gameId, Integer eraNumber, ActivistDeclarationRequest activistDeclarationRequest) {
        var result = recordActivistDeclarationUseCase.handle(new RecordActivistDeclarationUseCase.Command(
                gameId,
                eraNumber,
                CurrentPlayer.id(),
                ActionRestMapper.toDomain(activistDeclarationRequest.getSpecialAction()),
                activistDeclarationRequest.getTargetEventId(),
                activistDeclarationRequest.getTargetOutcomeId()));
        return ResponseEntity.accepted()
                .body(new ActivistDeclarationResponse(
                        result.gameId(),
                        result.eraNumber(),
                        result.playerId(),
                        ActionRestMapper.toRest(result.mode()),
                        result.targetEventId(),
                        result.targetOutcomeId(),
                        ActivistDeclarationResponse.StatusEnum.DECLARED));
    }

    @Override
    public ResponseEntity<SubmitActionResponse> submitAction(
            UUID gameId, Integer eraNumber, Integer roundNumber, SubmitActionRequest submitActionRequest) {
        var playerId = CurrentPlayer.id();
        SubmitRoundActionUseCase.Command command = switch (submitActionRequest.getActionType()) {
            case CARD -> card(gameId, eraNumber, roundNumber, playerId, (CardActionRequest) submitActionRequest);
            case SPECIAL ->
                special(gameId, eraNumber, roundNumber, playerId, (SpecialActionRequest) submitActionRequest);
            case PASS -> new SubmitRoundActionUseCase.Pass(gameId, eraNumber, roundNumber, playerId);
        };
        var result = submitRoundActionUseCase.handle(command);

        return ResponseEntity.accepted()
                .body(new SubmitActionResponse(
                        result.gameId(),
                        result.eraNumber(),
                        result.roundNumber(),
                        result.playerId(),
                        ActionSubmissionStatus.SUBMITTED,
                        result.roundClosed()));
    }

    private static SubmitRoundActionUseCase.PlayCard card(
            UUID gameId, int eraNumber, int roundNumber, UUID playerId, CardActionRequest request) {
        return new SubmitRoundActionUseCase.PlayCard(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                request.getCardInstanceId(),
                request.getTargetEventId(),
                request.getTargetEventIds(),
                request.getSourceOutcomeId(),
                request.getTargetOutcomeId(),
                request.getTargetPlayerId(),
                request.getTargetPlayerIds(),
                ActionRestMapper.toDomain(request.getDisguiseCategory()));
    }

    private static SubmitRoundActionUseCase.PlaySpecial special(
            UUID gameId, int eraNumber, int roundNumber, UUID playerId, SpecialActionRequest request) {
        return new SubmitRoundActionUseCase.PlaySpecial(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                ActionRestMapper.toDomain(request.getSpecialAction()),
                request.getSourceEventId(),
                request.getSourceOutcomeId(),
                request.getTargetEventId(),
                request.getTargetOutcomeId(),
                request.getTargetPlayerId());
    }
}
