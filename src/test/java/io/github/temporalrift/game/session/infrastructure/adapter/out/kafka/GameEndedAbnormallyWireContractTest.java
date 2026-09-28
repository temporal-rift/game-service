package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;

class GameEndedAbnormallyWireContractTest {

    private final SessionEventWireMapper mapper = Mappers.getMapper(SessionEventWireMapper.class);

    @Test
    void wirePayload_carriesTheDocumentedReasonValues() {
        var gameId = UUID.randomUUID();

        var deckExhausted = mapper.toWire(new GameEndedAbnormally(gameId, GameEndedAbnormally.Reason.DECK_EXHAUSTED));
        var resolutionFailed =
                mapper.toWire(new GameEndedAbnormally(gameId, GameEndedAbnormally.Reason.RESOLUTION_FAILED));

        assertThat(deckExhausted.gameId()).isEqualTo(gameId);
        assertThat(deckExhausted.reason()).isEqualTo("deck-exhausted");
        assertThat(resolutionFailed.reason()).isEqualTo("resolution-failed");
    }
}
