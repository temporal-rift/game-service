package io.github.temporalrift.game.session.domain.ending;

/**
 * The timeline-stabilization claim rule. When {@code factionClaims} is on, a Prophet or Weaver with at least the given
 * objective progress wins stabilization; when it is off (the default rule), no faction claims it and the highest score
 * decides.
 */
public record StabilizationThresholds(
        int prophetWrittenResolutions, int weaverActiveChainLinks, boolean factionClaims) {

    public StabilizationThresholds {
        if (prophetWrittenResolutions < 1) {
            throw new IllegalArgumentException("prophetWrittenResolutions must be positive");
        }
        if (weaverActiveChainLinks < 1) {
            throw new IllegalArgumentException("weaverActiveChainLinks must be positive");
        }
    }
}
