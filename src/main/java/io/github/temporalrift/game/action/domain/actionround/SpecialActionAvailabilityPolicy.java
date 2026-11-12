package io.github.temporalrift.game.action.domain.actionround;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Decides, for one player at the opening of an action round, which of the faction's specials
 * {@code submitAction} would reject for a reason that does not depend on the submission's target.
 * Target-dependent checks and prerequisites judged by timeline-service are deliberately not represented.
 */
public final class SpecialActionAvailabilityPolicy {

    private SpecialActionAvailabilityPolicy() {}

    /** The rules in force for the round being opened. */
    public record RoundRules(
            int eraNumber,
            int roundNumber,
            int maxEras,
            SpecialActionRoundWindows roundWindows,
            Set<SpecialAction> onceEraBudgetedSpecials,
            int sealMaxUsesPerGame) {

        public RoundRules {
            onceEraBudgetedSpecials = Set.copyOf(onceEraBudgetedSpecials);
        }
    }

    /** What the player has already done that the rules count against them. */
    public record PlayerUsage(boolean jammed, Set<SpecialAction> claimedThisEra, int acceptedSeals) {

        public PlayerUsage {
            claimedThisEra = Set.copyOf(claimedThisEra);
        }
    }

    public record Availability(SpecialAction specialAction, SpecialActionUnavailableReason unavailableReason) {

        public boolean available() {
            return unavailableReason == null;
        }
    }

    /** The faction's three specials in a stable order, each with the first unavailability reason that applies. */
    public static List<Availability> evaluate(Faction faction, RoundRules rules, PlayerUsage usage) {
        return faction.getSpecialActions().stream()
                .sorted(Comparator.comparing(SpecialAction::ordinal))
                .map(special -> new Availability(
                        special, unavailableReason(special, rules, usage).orElse(null)))
                .toList();
    }

    private static Optional<SpecialActionUnavailableReason> unavailableReason(
            SpecialAction special, RoundRules rules, PlayerUsage usage) {
        return Arrays.stream(SpecialActionUnavailableReason.values())
                .filter(reason -> applies(reason, special, rules, usage))
                .findFirst();
    }

    // Declaration order of the enum is the precedence order.
    private static boolean applies(
            SpecialActionUnavailableReason reason, SpecialAction special, RoundRules rules, PlayerUsage usage) {
        return switch (reason) {
            case JAMMED -> usage.jammed();
            case DECLARATION_WINDOW_ONLY -> SpecialActionRoundRules.declarationWindowOnly(special);
            case NOT_ELIGIBLE_THIS_ERA ->
                SpecialActionRoundRules.ineligibleThisEra(special, rules.eraNumber(), rules.maxEras());
            case NOT_ELIGIBLE_THIS_ROUND ->
                SpecialActionRoundRules.ineligibleThisRound(special, rules.roundNumber(), rules.roundWindows());
            case GAME_BUDGET_EXHAUSTED ->
                special == SpecialAction.SEAL && usage.acceptedSeals() >= rules.sealMaxUsesPerGame();
            case ERA_BUDGET_EXHAUSTED ->
                rules.onceEraBudgetedSpecials().contains(special)
                        && usage.claimedThisEra().contains(special);
        };
    }
}
