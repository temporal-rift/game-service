package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

class RoundCancellationTest {

    @Test
    void cancelledPlayerIds_considersMutualNullifySimultaneously() {
        var firstPlayerId = UUID.randomUUID();
        var secondPlayerId = UUID.randomUUID();

        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(
                List.of(nullify(firstPlayerId, secondPlayerId), nullify(secondPlayerId, firstPlayerId)));

        assertThat(cancelledPlayerIds).containsExactlyInAnyOrder(firstPlayerId, secondPlayerId);
    }

    @Test
    void gradeTwoNullify_cancelsBothNamedPlayers() {
        var nullifyingPlayerId = UUID.randomUUID();
        var firstTargetId = UUID.randomUUID();
        var secondTargetId = UUID.randomUUID();

        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(List.of(
                nullify(nullifyingPlayerId, CardGrade.II, List.of(firstTargetId, secondTargetId)),
                push(firstTargetId),
                push(secondTargetId)));

        assertThat(cancelledPlayerIds).containsExactlyInAnyOrder(firstTargetId, secondTargetId);
    }

    @Test
    void gradeTwoNullify_namingANonSubmitter_cancelsOnlyTheSubmitter() {
        var nullifyingPlayerId = UUID.randomUUID();
        var targetId = UUID.randomUUID();

        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(List.of(
                nullify(nullifyingPlayerId, CardGrade.II, List.of(targetId, UUID.randomUUID())), push(targetId)));

        assertThat(cancelledPlayerIds).containsExactly(targetId);
    }

    @Test
    void gradeOneNullify_cancelsItsSingleNamedPlayer() {
        var nullifyingPlayerId = UUID.randomUUID();
        var targetId = UUID.randomUUID();

        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(
                List.of(nullify(nullifyingPlayerId, CardGrade.I, List.of(targetId)), push(targetId)));

        assertThat(cancelledPlayerIds).containsExactly(targetId);
    }

    private static SubmittedAction.CardAction nullify(UUID playerId, UUID targetPlayerId) {
        return new SubmittedAction.CardAction(
                playerId, UUID.randomUUID(), CardType.NULLIFY, CardGrade.I, null, null, null, targetPlayerId);
    }

    private static SubmittedAction.CardAction nullify(UUID playerId, CardGrade grade, List<UUID> targetPlayerIds) {
        return new SubmittedAction.CardAction(
                playerId,
                UUID.randomUUID(),
                CardType.NULLIFY,
                grade,
                null,
                null,
                null,
                null,
                null,
                targetPlayerIds,
                null);
    }

    private static SubmittedAction.CardAction push(UUID playerId) {
        return new SubmittedAction.CardAction(
                playerId,
                UUID.randomUUID(),
                CardType.PUSH,
                CardGrade.I,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null);
    }
}
