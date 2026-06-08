package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.ActionSubmissionStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.CardActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.RoundStatus;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.RoundStatusResponse;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.SpecialActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.SubmitActionRequest;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.SubmitActionResponse;
import io.github.temporalrift.game.shared.PlayerPrincipal;

@RestController
class ActionController implements ActionApi {

    private final PlayCardUseCase playCardUseCase;

    private final PlaySpecialActionUseCase playSpecialActionUseCase;

    private final GetRoundStatusUseCase getRoundStatusUseCase;

    ActionController(
            PlayCardUseCase playCardUseCase,
            PlaySpecialActionUseCase playSpecialActionUseCase,
            GetRoundStatusUseCase getRoundStatusUseCase) {
        this.playCardUseCase = playCardUseCase;
        this.playSpecialActionUseCase = playSpecialActionUseCase;
        this.getRoundStatusUseCase = getRoundStatusUseCase;
    }

    @Override
    public ResponseEntity<SubmitActionResponse> submitAction(
            UUID gameId, Integer eraNumber, Integer roundNumber, SubmitActionRequest submitActionRequest) {
        var playerId = callerPlayerId();
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
        var result = getRoundStatusUseCase.handle(new GetRoundStatusUseCase.Query(gameId, eraNumber, roundNumber));
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
                io.github.temporalrift.events.shared.SpecialAction.valueOf(
                        request.getSpecialAction().name()),
                request.getTargetEventId(),
                request.getTargetOutcomeId(),
                request.getTargetPlayerId()));
        return new SubmissionResult(
                result.gameId(), result.eraNumber(), result.roundNumber(), result.playerId(), result.roundClosed());
    }

    private UUID callerPlayerId() {
        return ((PlayerPrincipal)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .playerId();
    }

    private record SubmissionResult(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
