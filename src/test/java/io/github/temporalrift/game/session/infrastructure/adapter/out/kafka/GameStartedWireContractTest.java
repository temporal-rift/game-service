package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.shared.domain.event.GameStarted;

class GameStartedWireContractTest {

    private final SessionEventWireMapper mapper = Mappers.getMapper(SessionEventWireMapper.class);

    @Test
    void wirePayload_carriesTheWinScoreThreshold() {
        var event = new GameStarted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new GameStarted.Player(UUID.randomUUID(), "Ada")),
                1,
                30,
                25);

        var wire = mapper.toWire(event);

        assertThat(wire.winScoreThreshold()).isEqualTo(25);
    }
}
