package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SpecialAction;

class ActionRestMapperTest {

    @ParameterizedTest
    @EnumSource(SpecialAction.class)
    void toDomain_mapsEveryGeneratedApiAction(SpecialAction apiAction) {
        assertThat(ActionRestMapper.toDomain(apiAction))
                .isEqualTo(io.github.temporalrift.game.shared.SpecialAction.valueOf(apiAction.name()));
    }

    @ParameterizedTest
    @EnumSource(
            io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode.class)
    void toDomain_mapsEveryGeneratedActivistDeclarationMode(
            io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode
                    apiMode) {
        assertThat(ActionRestMapper.toDomain(apiMode)).isEqualTo(ActivistDeclarationMode.valueOf(apiMode.name()));
    }

    @ParameterizedTest
    @EnumSource(ActivistDeclarationMode.class)
    void toRest_mapsEveryDomainActivistDeclarationMode(ActivistDeclarationMode domainMode) {
        assertThat(ActionRestMapper.toRest(domainMode).name()).isEqualTo(domainMode.name());
    }
}
