package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.PassActionRoundUseCase;
import io.github.temporalrift.game.action.application.port.in.PassParadoxResolutionUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.SelectHandUseCase;
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

    private final PlayCardUseCase playCardUseCase;

    private final PlaySpecialActionUseCase playSpecialActionUseCase;

    private final PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase;

    private final PassActionRoundUseCase passActionRoundUseCase;

    private final PassParadoxResolutionUseCase passParadoxResolutionUseCase;

    private final RecordActivistDeclarationUseCase recordActivistDeclarationUseCase;

    private final SelectHandUseCase selectHandUseCase;

    ActionController(
            PlayCardUseCase playCardUseCase,
            PlaySpecialActionUseCase playSpecialActionUseCase,
            PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase,
            PassActionRoundUseCase passActionRoundUseCase,
            PassParadoxResolutionUseCase passParadoxResolutionUseCase,
            RecordActivistDeclarationUseCase recordActivistDeclarationUseCase,
            SelectHandUseCase selectHandUseCase,
            DeclineDeclarationUseCase declineDeclarationUseCase) {
        this.playCardUseCase = playCardUseCase;
        this.playSpecialActionUseCase = playSpecialActionUseCase;
        this.playParadoxResolutionCardUseCase = playParadoxResolutionCardUseCase;
        this.passActionRoundUseCase = passActionRoundUseCase;
        this.passParadoxResolutionUseCase = passParadoxResolutionUseCase;
        this.recordActivistDeclarationUseCase = recordActivistDeclarationUseCase;
        this.selectHandUseCase = selectHandUseCase;
        this.declineDeclarationUseCase = declineDeclarationUseCase;
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
        var resultPlayerId = switch (actionType) {
            case CARD -> submitParadoxResolutionCard(gameId, eraNumber, playerId, request);
            case PASS -> passParadoxResolution(gameId, eraNumber, playerId, request);
            case SPECIAL -> throw InvalidParadoxResolutionRequestException.specialNotEligible();
        };
        return ResponseEntity.accepted()
                .body(new ParadoxResolutionCardResponse(
                        gameId, eraNumber, resultPlayerId, ActionSubmissionStatus.SUBMITTED));
    }

    private UUID submitParadoxResolutionCard(
            UUID gameId, int eraNumber, UUID playerId, ParadoxResolutionCardRequest request) {
        if (request.getCardInstanceId() == null
                || request.getTargetEventId() == null
                || request.getTargetOutcomeId() == null) {
            throw InvalidParadoxResolutionRequestException.cardRequiresAllFields();
        }
        return playParadoxResolutionCardUseCase
                .handle(new PlayParadoxResolutionCardUseCase.Command(
                        gameId,
                        eraNumber,
                        playerId,
                        request.getCardInstanceId(),
                        request.getTargetEventId(),
                        request.getTargetOutcomeId()))
                .playerId();
    }

    private UUID passParadoxResolution(
            UUID gameId, int eraNumber, UUID playerId, ParadoxResolutionCardRequest request) {
        if (request.getCardInstanceId() != null
                || request.getTargetEventId() != null
                || request.getTargetOutcomeId() != null) {
            throw InvalidParadoxResolutionRequestException.passCarriesCardFields();
        }
        return passParadoxResolutionUseCase
                .handle(new PassParadoxResolutionUseCase.Command(gameId, eraNumber, playerId))
                .playerId();
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
        var result = switch (submitActionRequest.getActionType()) {
            case CARD -> submitCard(gameId, eraNumber, roundNumber, playerId, (CardActionRequest) submitActionRequest);
            case SPECIAL ->
                submitSpecial(gameId, eraNumber, roundNumber, playerId, (SpecialActionRequest) submitActionRequest);
            case PASS -> pass(gameId, eraNumber, roundNumber, playerId);
        };

        return ResponseEntity.accepted()
                .body(new SubmitActionResponse(
                        result.gameId(),
                        result.eraNumber(),
                        result.roundNumber(),
                        result.playerId(),
                        ActionSubmissionStatus.SUBMITTED,
                        result.roundClosed()));
    }

    private SubmissionResult submitCard(
            UUID gameId, int eraNumber, int roundNumber, UUID playerId, CardActionRequest request) {
        var result = playCardUseCase.handle(new PlayCardUseCase.Command(
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
                ActionRestMapper.toDomain(request.getDisguiseCategory())));
        return new SubmissionResult(
                result.gameId(), result.eraNumber(), result.roundNumber(), result.playerId(), result.roundClosed());
    }

    private SubmissionResult submitSpecial(
            UUID gameId, int eraNumber, int roundNumber, UUID playerId, SpecialActionRequest request) {
        var result = playSpecialActionUseCase.handle(new PlaySpecialActionUseCase.Command(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                ActionRestMapper.toDomain(request.getSpecialAction()),
                request.getSourceEventId(),
                request.getSourceOutcomeId(),
                request.getTargetEventId(),
                request.getTargetOutcomeId(),
                request.getTargetPlayerId()));
        return new SubmissionResult(
                result.gameId(), result.eraNumber(), result.roundNumber(), result.playerId(), result.roundClosed());
    }

    private SubmissionResult pass(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {
        var result = passActionRoundUseCase.handle(
                new PassActionRoundUseCase.Command(gameId, eraNumber, roundNumber, playerId));
        return new SubmissionResult(
                result.gameId(), result.eraNumber(), result.roundNumber(), result.playerId(), result.roundClosed());
    }

    private record SubmissionResult(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
