package io.github.temporalrift.game.scoring;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public final class ScoreRulesTestValues {

    private ScoreRulesTestValues() {}

    public static int pointsDelta(ScoreReason reason) {
        return switch (reason) {
            case ANNIHILATED_OUTCOME -> 3;
            case CORRUPTED_OPPONENT_CARD,
                    CHAIN_LINK_ADDED,
                    MIMIC_CONTRIBUTED_TO_WIN,
                    EXPOSE_CHANGED_PLAYER_BEHAVIOR,
                    EXPOSE_SIGNATURE_REVEALED -> 2;
            case EVENT_RESOLVED_AS_WRITTEN, SECRET_OUTCOME_WON -> 4;
            case DECLARED_OUTCOME_WON -> 5;
            case DECLARED_OUTCOME_WON_WITH_RALLY -> 6;
            case FULFILLMENT_SUCCEEDED -> 8;
            case EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN, PARADOX_CASCADE_PENALTY -> -2;
            case FACTION_UNIDENTIFIED -> 6;
            case CHAIN_COMPLETED -> 10;
            case CHAIN_BROKEN -> -3;
        };
    }
}
