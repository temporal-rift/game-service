package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;

class ActionEventWireMapperTest {

    private final ActionEventWireMapper mapper = Mappers.getMapper(ActionEventWireMapper.class);

    @Test
    void exposeBehaviorChanged_mapsToTheContractPayloadShape() {
        var gameId = UUID.randomUUID();
        var activistPlayerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var domain = new ExposeBehaviorChanged(gameId, 2, 3, activistPlayerId, targetPlayerId);

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(gameId);
        assertThat(wire.eraNumber()).isEqualTo(2);
        assertThat(wire.roundNumber()).isEqualTo(3);
        assertThat(wire.activistPlayerId()).isEqualTo(activistPlayerId);
        assertThat(wire.targetPlayerId()).isEqualTo(targetPlayerId);
    }
}
