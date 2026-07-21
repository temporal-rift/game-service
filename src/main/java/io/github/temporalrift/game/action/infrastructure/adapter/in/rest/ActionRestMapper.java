package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model.SpecialAction;

/** Maps generated action API values to their domain representation. */
final class ActionRestMapper {

    private ActionRestMapper() {}

    static io.github.temporalrift.game.shared.SpecialAction toDomain(SpecialAction action) {
        return switch (action) {
            case ANNIHILATE -> io.github.temporalrift.game.shared.SpecialAction.ANNIHILATE;
            case CORRUPT -> io.github.temporalrift.game.shared.SpecialAction.CORRUPT;
            case CASCADE -> io.github.temporalrift.game.shared.SpecialAction.CASCADE;
            case FORESIGHT -> io.github.temporalrift.game.shared.SpecialAction.FORESIGHT;
            case SEAL -> io.github.temporalrift.game.shared.SpecialAction.SEAL;
            case FULFILLMENT -> io.github.temporalrift.game.shared.SpecialAction.FULFILLMENT;
            case REWRITE -> io.github.temporalrift.game.shared.SpecialAction.REWRITE;
            case MIMIC -> io.github.temporalrift.game.shared.SpecialAction.MIMIC;
            case OBSCURE -> io.github.temporalrift.game.shared.SpecialAction.OBSCURE;
            case THREAD -> io.github.temporalrift.game.shared.SpecialAction.THREAD;
            case TAPESTRY -> io.github.temporalrift.game.shared.SpecialAction.TAPESTRY;
            case UNRAVEL -> io.github.temporalrift.game.shared.SpecialAction.UNRAVEL;
            case RALLY -> io.github.temporalrift.game.shared.SpecialAction.RALLY;
            case EXPOSE -> io.github.temporalrift.game.shared.SpecialAction.EXPOSE;
            case MOMENTUM -> io.github.temporalrift.game.shared.SpecialAction.MOMENTUM;
        };
    }
}
