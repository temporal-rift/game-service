package io.github.temporalrift.game.shared.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A complete, valid set of the rules a game uses, as an execution context carries them. */
public final class ExecutionRulesFixture {

    private static final Map<String, Integer> SCORE_DELTAS = Map.ofEntries(
            Map.entry("ANNIHILATED_OUTCOME", 3),
            Map.entry("CORRUPTED_OPPONENT_CARD", 2),
            Map.entry("EVENT_RESOLVED_AS_WRITTEN", 4),
            Map.entry("FULFILLMENT_SUCCEEDED", 8),
            Map.entry("EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN", -2),
            Map.entry("SECRET_OUTCOME_WON", 4),
            Map.entry("MIMIC_NEVER_TRACED", 6),
            Map.entry("MIMIC_CONTRIBUTED_TO_WIN", 2),
            Map.entry("CHAIN_LINK_ADDED", 2),
            Map.entry("CHAIN_COMPLETED", 10),
            Map.entry("CHAIN_BROKEN", -3),
            Map.entry("DECLARED_OUTCOME_WON_WITH_RALLY", 6),
            Map.entry("DECLARED_OUTCOME_WON", 5),
            Map.entry("EXPOSE_SIGNATURE_REVEALED", 2),
            Map.entry("PARADOX_CASCADE_PENALTY", -2));

    private ExecutionRulesFixture() {}

    public static Map<String, Object> rules() {
        var rules = new LinkedHashMap<String, Object>();
        rules.put("game.rules.min-players", 3);
        rules.put("game.rules.max-players", 5);
        rules.put("game.rules.max-eras", 5);
        rules.put("game.rules.max-cascaded-paradoxes", 3);
        rules.put("game.rules.events-per-era", 3);
        rules.put("game.rules.cards-per-hand", 5);
        rules.put("game.rules.cards-per-deal", 7);
        rules.put("game.rules.win-score-threshold", 20);
        rules.put("game.rules.reconnect-grace-period-seconds", 30);
        rules.put("game.rules.action-round-timer-seconds.3", 60);
        rules.put("game.rules.action-round-timer-seconds.4", 45);
        rules.put("game.rules.action-round-timer-seconds.5", 30);
        rules.put("game.rules.hand-selection-timer-seconds", 60);
        rules.put("game.rules.declaration-timer-seconds.3", 2);
        rules.put("game.rules.declaration-timer-seconds.4", 2);
        rules.put("game.rules.declaration-timer-seconds.5", 2);
        rules.put("game.rules.card-category-weights.PROBABILITY_SHIFTER", 35);
        rules.put("game.rules.card-category-weights.INFORMATION", 25);
        rules.put("game.rules.card-category-weights.DISRUPTION", 25);
        rules.put("game.rules.card-category-weights.PARADOX", 15);
        rules.put("game.rules.card-grade-weights.I", 60);
        rules.put("game.rules.card-grade-weights.II", 30);
        rules.put("game.rules.card-grade-weights.III", 10);
        rules.put("game.rules.stabilization.prophet-written-resolutions", 3);
        rules.put("game.rules.stabilization.weaver-active-chain-links", 2);
        rules.put(
                "game.rules.once-era-budgeted-specials", List.of("ANNIHILATE", "SEAL", "CORRUPT", "MIMIC", "REWEAVE"));
        rules.put("game.rules.seal-max-uses-per-game", 2);
        rules.put("game.rules.hand-deal-forced-types", List.of());
        rules.put("game.rules.probability.floor", 0);
        rules.put("game.rules.probability.ceiling", 90);
        rules.put("game.rules.victory.eraser-annihilations", 4);
        rules.put("game.rules.victory.prophet-written-resolutions", 4);
        rules.put("game.rules.victory.revisionist-successful-eras", 3);
        rules.put("game.rules.victory.weaver-chain-length", 3);
        rules.put("game.rules.victory.activist-consecutive-declarations", 3);
        SCORE_DELTAS.forEach((reason, delta) -> rules.put("game.rules.scoring.score-deltas." + reason, delta));
        return rules;
    }

    public static Map<String, Object> rulesWith(String key, Object value) {
        var rules = rules();
        rules.put(key, value);
        return rules;
    }

    public static Map<String, Object> rulesWithout(String key) {
        var rules = rules();
        rules.remove(key);
        return rules;
    }
}
