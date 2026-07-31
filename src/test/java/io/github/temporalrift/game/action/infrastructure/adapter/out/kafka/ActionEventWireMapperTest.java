package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ExposeBehaviorChangedPayload;

class ActionEventWireMapperTest {

    private final ActionEventWireMapper mapper = Mappers.getMapper(ActionEventWireMapper.class);

    @Test
    void exposeBehaviorChanged_mapsOnlyTheFilteredPublicFields() {
        var payload =
                mapper.toWire(new ExposeBehaviorChanged(UUID.randomUUID(), 2, 3, UUID.randomUUID(), UUID.randomUUID()));

        assertThat(payload.getAdditionalProperties()).isEmpty();
        var fieldNames = Arrays.stream(ExposeBehaviorChangedPayload.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(fieldNames)
                .isNotEmpty()
                .doesNotContain("signature", "targetEventId", "sourceOutcomeId", "targetOutcomeId");
    }
}
