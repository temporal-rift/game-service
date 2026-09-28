package io.github.temporalrift.game.session.domain.ending;

/** Minimum objective progress with which a Prophet or Weaver wins timeline stabilization. */
public record StabilizationThresholds(int prophetWrittenResolutions, int weaverActiveChainLinks) {

    public StabilizationThresholds {
        if (prophetWrittenResolutions < 1) {
            throw new IllegalArgumentException("prophetWrittenResolutions must be positive");
        }
        if (weaverActiveChainLinks < 1) {
            throw new IllegalArgumentException("weaverActiveChainLinks must be positive");
        }
    }
}
