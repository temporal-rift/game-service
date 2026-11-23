package io.github.temporalrift.game.scoring.domain.port.out;

public interface VictoryRulesPort {

    /** Consequential leading annihilations an Eraser needs at a table of {@code playerCount} players. */
    int eraserAnnihilations(int playerCount);

    /** Events a Prophet needs resolved as written at a table of {@code playerCount} players. */
    int prophetWrittenResolutions(int playerCount);

    /** Winning eras a Revisionist needs at a table of {@code playerCount} players. */
    int revisionistSuccessfulEras(int playerCount);

    int weaverChainLength();

    int activistConsecutiveDeclarations();

    /**
     * Whether an Activist's successful eras must be consecutive at a table of {@code playerCount} players; where
     * they need not be, {@link #activistConsecutiveDeclarations()} successful eras in total meet the objective.
     */
    boolean activistRequiresConsecutiveEras(int playerCount);

    /**
     * How many steps fewer every faction objective needs in the final era, for a player who advanced it in that era;
     * 0 keeps the ordinary thresholds.
     */
    int finalEraObjectiveDiscount();
}
