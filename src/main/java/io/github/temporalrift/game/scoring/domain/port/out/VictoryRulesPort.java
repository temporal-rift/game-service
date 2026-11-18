package io.github.temporalrift.game.scoring.domain.port.out;

public interface VictoryRulesPort {

    int eraserAnnihilations();

    int prophetWrittenResolutions();

    int revisionistSuccessfulEras();

    int weaverChainLength();

    int activistConsecutiveDeclarations();

    /**
     * How many steps fewer every faction objective needs in the final era, for a player who advanced it in that era;
     * 0 keeps the ordinary thresholds.
     */
    int finalEraObjectiveDiscount();
}
