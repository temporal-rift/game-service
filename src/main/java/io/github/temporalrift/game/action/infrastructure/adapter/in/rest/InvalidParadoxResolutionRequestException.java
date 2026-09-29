package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

/**
 * A paradox-resolution document whose fields do not match its {@code actionType}. The contract no longer
 * marks the card fields required, because a pass omits them, so the variant is checked here instead.
 */
final class InvalidParadoxResolutionRequestException extends RuntimeException {

    private InvalidParadoxResolutionRequestException(String message) {
        super(message);
    }

    static InvalidParadoxResolutionRequestException cardRequiresAllFields() {
        return new InvalidParadoxResolutionRequestException(
                "A paradox-resolution card submission requires cardInstanceId, targetEventId, and targetOutcomeId");
    }

    static InvalidParadoxResolutionRequestException passCarriesCardFields() {
        return new InvalidParadoxResolutionRequestException(
                "A paradox-resolution pass must omit cardInstanceId, targetEventId, and targetOutcomeId");
    }

    static InvalidParadoxResolutionRequestException specialNotEligible() {
        return new InvalidParadoxResolutionRequestException(
                "A special action cannot be submitted during paradox resolution");
    }
}
