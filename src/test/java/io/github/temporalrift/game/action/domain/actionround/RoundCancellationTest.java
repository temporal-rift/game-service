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

    private static SubmittedAction.CardAction nullify(UUID playerId, UUID targetPlayerId) {
        return new SubmittedAction.CardAction(
                playerId, UUID.randomUUID(), CardType.NULLIFY, CardGrade.I, null, null, null, targetPlayerId);
    }
}
