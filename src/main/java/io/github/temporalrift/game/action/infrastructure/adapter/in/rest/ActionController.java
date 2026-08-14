package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.application.port.in.SelectHandUseCase;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.ActionApi;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActionSubmissionStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.CardActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.HandSelectionStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ParadoxResolutionCardRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ParadoxResolutionCardResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.RoundStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.RoundStatusResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SpecialActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SubmitActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SubmitActionResponse;
import io.github.temporalrift.game.shared.CurrentPlayer;

@RestController
class ActionController implements ActionApi {

    private final PlayCardUseCase playCardUseCase;

    private final PlaySpecialActionUseCase playSpecialActionUseCase;

    private final PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase;

    private final RecordActivistDeclarationUseCase recordActivistDeclarationUseCase;

    private final GetRoundStatusUseCase getRoundStatusUseCase;
    private final SelectHandUseCase selectHandUseCase;

    ActionController(
            PlayCardUseCase playCardUseCase,
            PlaySpecialActionUseCase playSpecialActionUseCase,
            PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase,
            RecordActivistDeclarationUseCase recordActivistDeclarationUseCase,
            GetRoundStatusUseCase getRoundStatusUseCase,
            SelectHandUseCase selectHandUseCase) {
        this.playCardUseCase = playCardUseCase;
        this.playSpecialActionUseCase = playSpecialActionUseCase;
        this.playParadoxResolutionCardUseCase = playParadoxResolutionCardUseCase;
        this.recordActivistDeclarationUseCase = recordActivistDeclarationUseCase;
        this.getRoundStatusUseCase = getRoundStatusUseCase;
        this.selectHandUseCase = selectHandUseCase;
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
        var result = playParadoxResolutionCardUseCase.handle(new PlayParadoxResolutionCardUseCase.Command(
                gameId,
                eraNumber,
                CurrentPlayer.id(),
                request.getCardInstanceId(),
                request.getTargetEventId(),
                request.getTargetOutcomeId()));
        return ResponseEntity.accepted()
                .body(new ParadoxResolutionCardResponse(
                        result.gameId(), result.eraNumber(), result.playerId(), ActionSubmissionStatus.SUBMITTED));
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
        var result =
                switch (submitActionRequest.getActionType()) {
                    case CARD ->
                        submitCard(gameId, eraNumber, roundNumber, playerId, (CardActionRequest) submitActionRequest);
                    case SPECIAL ->
                        submitSpecial(
                                gameId, eraNumber, roundNumber, playerId, (SpecialActionRequest) submitActionRequest);
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

    @Override
    public ResponseEntity<RoundStatusResponse> getRoundStatus(UUID gameId, Integer eraNumber, Integer roundNumber) {
        var result = getRoundStatusUseCase.handle(
                new GetRoundStatusUseCase.Query(gameId, eraNumber, roundNumber, CurrentPlayer.id()));
        return ResponseEntity.ok(new RoundStatusResponse(
                result.eraNumber(),
                result.roundNumber(),
                RoundStatus.fromValue(result.status()),
                result.timerRemainingSeconds(),
                result.submittedCount(),
                result.totalPlayers(),
                result.pendingPlayerIds()));
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
                request.getSourceOutcomeId(),
                request.getTargetOutcomeId()));
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
                request.getTargetEventId(),
                request.getTargetOutcomeId(),
                request.getTargetPlayerId()));
        return new SubmissionResult(
                result.gameId(), result.eraNumber(), result.roundNumber(), result.playerId(), result.roundClosed());
    }

    private record SubmissionResult(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
