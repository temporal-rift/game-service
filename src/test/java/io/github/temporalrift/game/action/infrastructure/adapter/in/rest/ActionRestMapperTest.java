package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.SpecialAction;

class ActionRestMapperTest {

    @ParameterizedTest
    @EnumSource(SpecialAction.class)
    void toDomain_mapsEveryGeneratedApiAction(SpecialAction apiAction) {
        assertThat(ActionRestMapper.toDomain(apiAction))
                .isEqualTo(io.github.temporalrift.game.shared.SpecialAction.valueOf(apiAction.name()));
    }
}
