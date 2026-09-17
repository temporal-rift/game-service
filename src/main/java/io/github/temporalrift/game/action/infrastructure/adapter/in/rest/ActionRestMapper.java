package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SpecialAction;

/** Maps generated action API values to their domain representation. */
final class ActionRestMapper {

    private ActionRestMapper() {}

    static io.github.temporalrift.game.shared.domain.model.SpecialAction toDomain(SpecialAction action) {
        return switch (action) {
            case ANNIHILATE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.ANNIHILATE;
            case CORRUPT -> io.github.temporalrift.game.shared.domain.model.SpecialAction.CORRUPT;
            case CASCADE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.CASCADE;
            case FORESIGHT -> io.github.temporalrift.game.shared.domain.model.SpecialAction.FORESIGHT;
            case SEAL -> io.github.temporalrift.game.shared.domain.model.SpecialAction.SEAL;
            case FULFILLMENT -> io.github.temporalrift.game.shared.domain.model.SpecialAction.FULFILLMENT;
            case REWRITE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.REWRITE;
            case MIMIC -> io.github.temporalrift.game.shared.domain.model.SpecialAction.MIMIC;
            case OBSCURE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.OBSCURE;
            case THREAD -> io.github.temporalrift.game.shared.domain.model.SpecialAction.THREAD;
            case TAPESTRY -> io.github.temporalrift.game.shared.domain.model.SpecialAction.TAPESTRY;
            case REWEAVE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.REWEAVE;
            case RALLY -> io.github.temporalrift.game.shared.domain.model.SpecialAction.RALLY;
            case EXPOSE -> io.github.temporalrift.game.shared.domain.model.SpecialAction.EXPOSE;
            case MOMENTUM -> io.github.temporalrift.game.shared.domain.model.SpecialAction.MOMENTUM;
        };
    }

    static io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode toDomain(
            io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode mode) {
        return switch (mode) {
            case RALLY -> io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode.RALLY;
            case MOMENTUM ->
                io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode.MOMENTUM;
        };
    }

    static io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode toRest(
            io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode mode) {
        return switch (mode) {
            case RALLY ->
                io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode
                        .RALLY;
            case MOMENTUM ->
                io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.ActivistDeclarationMode
                        .MOMENTUM;
        };
    }
}
