package io.github.temporalrift.game.scoring;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public final class ScoreRulesTestValues {

    private ScoreRulesTestValues() {}

    public static int pointsDelta(ScoreReason reason) {
        return switch (reason) {
            case ANNIHILATED_OUTCOME -> 3;
            case CORRUPTED_OPPONENT_CARD, CHAIN_LINK_ADDED, MIMIC_CONTRIBUTED_TO_WIN, EXPOSE_CHANGED_PLAYER_BEHAVIOR ->
                2;
            case ERA_ENDED_WITH_FEWER_OUTCOMES -> 5;
            case EVENT_RESOLVED_AS_WRITTEN, SECRET_OUTCOME_WON, DECLARED_OUTCOME_WON -> 4;
            case FULFILLMENT_SUCCEEDED, DECLARED_OUTCOME_WON_WITH_RALLY -> 8;
            case EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN, PARADOX_CASCADE_PENALTY -> -2;
            case FACTION_UNIDENTIFIED -> 6;
            case CHAIN_COMPLETED -> 10;
            case CHAIN_BROKEN -> -3;
        };
    }
}
