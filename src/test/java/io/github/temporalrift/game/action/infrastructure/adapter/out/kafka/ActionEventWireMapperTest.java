package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.shared.CardType;

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

    @Test
    void paradoxResolutionCardPlayed_mapsWithoutRoundCoordinates() {
        var domain = new ParadoxResolutionCardPlayed(
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CardType.DETONATE,
                UUID.randomUUID(),
                UUID.randomUUID());

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(domain.gameId());
        assertThat(wire.eraNumber()).isEqualTo(domain.eraNumber());
        assertThat(wire.playerId()).isEqualTo(domain.playerId());
        assertThat(wire.cardInstanceId()).isEqualTo(domain.cardInstanceId());
        assertThat(wire.cardType().name()).isEqualTo("DETONATE");
        assertThat(wire.targetEventId()).isEqualTo(domain.targetEventId());
        assertThat(wire.targetOutcomeId()).isEqualTo(domain.targetOutcomeId());
    }
}
