package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy.Availability;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy.PlayerUsage;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy.RoundRules;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

class SpecialActionAvailabilityPolicyTest {

    private static final int MAX_ERAS = 5;
    private static final int ANNIHILATE_EARLIEST = 2;
    private static final int SEAL_MAX = 2;
    private static final Set<SpecialAction> ERA_BUDGETED = Set.of(SpecialAction.ANNIHILATE, SpecialAction.SEAL);

    private static RoundRules rules(int era, int round) {
        return new RoundRules(era, round, MAX_ERAS, ANNIHILATE_EARLIEST, ERA_BUDGETED, SEAL_MAX);
    }

    private static PlayerUsage fresh() {
        return new PlayerUsage(false, Set.of(), 0);
    }

    private static Map<SpecialAction, SpecialActionUnavailableReason> reasons(List<Availability> result) {
        return result.stream()
                .filter(entry -> !entry.available())
                .collect(Collectors.toMap(Availability::specialAction, Availability::unavailableReason));
    }

    @Test
    @DisplayName("lists exactly the faction's three specials")
    void listsTheThreeFactionSpecials() {
        for (var faction : Faction.values()) {
            var result = SpecialActionAvailabilityPolicy.evaluate(faction, rules(1, 2), fresh());

            assertThat(result)
                    .extracting(Availability::specialAction)
                    .containsExactlyInAnyOrderElementsOf(faction.getSpecialActions());
        }
    }

    @Test
    @DisplayName("an Eraser cannot Annihilate before the earliest round")
    void annihilateBeforeEarliestRound() {
        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.ERASERS, rules(1, 1), fresh());

        assertThat(reasons(result))
                .containsOnly(
                        Map.entry(SpecialAction.ANNIHILATE, SpecialActionUnavailableReason.NOT_ELIGIBLE_THIS_ROUND));
    }

    @Test
    @DisplayName("an accepted Annihilate exhausts the era budget, which returns next era")
    void annihilateEraBudget() {
        var spent = new PlayerUsage(false, Set.of(SpecialAction.ANNIHILATE), 0);

        assertThat(reasons(SpecialActionAvailabilityPolicy.evaluate(Faction.ERASERS, rules(1, 3), spent)))
                .containsOnly(Map.entry(SpecialAction.ANNIHILATE, SpecialActionUnavailableReason.ERA_BUDGET_EXHAUSTED));
        assertThat(reasons(SpecialActionAvailabilityPolicy.evaluate(Faction.ERASERS, rules(2, 2), fresh())))
                .isEmpty();
    }

    @Test
    @DisplayName("Cascade is unavailable in the final era")
    void cascadeInFinalEra() {
        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.ERASERS, rules(5, 2), fresh());

        assertThat(reasons(result))
                .containsOnly(Map.entry(SpecialAction.CASCADE, SpecialActionUnavailableReason.NOT_ELIGIBLE_THIS_ERA));
    }

    @Test
    @DisplayName("Seal is unavailable once its game budget is spent, even in a fresh era")
    void sealGameBudget() {
        var spent = new PlayerUsage(false, Set.of(), 2);

        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.PROPHETS, rules(3, 1), spent);

        assertThat(reasons(result))
                .containsOnly(Map.entry(SpecialAction.SEAL, SpecialActionUnavailableReason.GAME_BUDGET_EXHAUSTED));
    }

    @Test
    @DisplayName("game budget outranks era budget for Seal")
    void gameBudgetOutranksEraBudget() {
        var spent = new PlayerUsage(false, Set.of(SpecialAction.SEAL), 2);

        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.PROPHETS, rules(2, 2), spent);

        assertThat(reasons(result).get(SpecialAction.SEAL))
                .isEqualTo(SpecialActionUnavailableReason.GAME_BUDGET_EXHAUSTED);
    }

    @Test
    @DisplayName("a jammed player has all three specials unavailable as JAMMED, outranking everything")
    void jamOutranksEverything() {
        var jammed = new PlayerUsage(true, Set.of(SpecialAction.MIMIC), 0);

        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.REVISIONISTS, rules(1, 3), jammed);

        assertThat(result).hasSize(3).allSatisfy(entry -> {
            assertThat(entry.available()).isFalse();
            assertThat(entry.unavailableReason()).isEqualTo(SpecialActionUnavailableReason.JAMMED);
        });
    }

    @Test
    @DisplayName("an Activist's Round 1 offers neither declaration specials nor Expose; Round 2 offers Expose")
    void activistRounds() {
        assertThat(reasons(SpecialActionAvailabilityPolicy.evaluate(Faction.ACTIVISTS, rules(1, 1), fresh())))
                .containsOnly(
                        Map.entry(SpecialAction.RALLY, SpecialActionUnavailableReason.DECLARATION_WINDOW_ONLY),
                        Map.entry(SpecialAction.MOMENTUM, SpecialActionUnavailableReason.DECLARATION_WINDOW_ONLY),
                        Map.entry(SpecialAction.EXPOSE, SpecialActionUnavailableReason.NOT_ELIGIBLE_THIS_ROUND));
        assertThat(reasons(SpecialActionAvailabilityPolicy.evaluate(Faction.ACTIVISTS, rules(1, 2), fresh())))
                .doesNotContainKey(SpecialAction.EXPOSE);
    }

    @Test
    @DisplayName("Obscure is unavailable in Round 3")
    void obscureInRoundThree() {
        var result = SpecialActionAvailabilityPolicy.evaluate(Faction.REVISIONISTS, rules(1, 3), fresh());

        assertThat(reasons(result))
                .containsOnly(Map.entry(SpecialAction.OBSCURE, SpecialActionUnavailableReason.NOT_ELIGIBLE_THIS_ROUND));
    }

    @Test
    @DisplayName("every unavailable reason is also rejected by the rules submitAction applies")
    void reportedRoundRulesMatchSubmission() {
        for (var faction : Faction.values()) {
            for (var era : List.of(1, MAX_ERAS)) {
                for (var round = 1; round <= 3; round++) {
                    for (var entry : SpecialActionAvailabilityPolicy.evaluate(faction, rules(era, round), fresh())) {
                        assertThat(entry.available())
                                .as("%s %s era %d round %d", faction, entry.specialAction(), era, round)
                                .isEqualTo(submissionAccepts(entry.specialAction(), era, round));
                    }
                }
            }
        }
    }

    // The same validation the submit path runs before any budget or target is considered.
    private static boolean submissionAccepts(SpecialAction special, int era, int round) {
        var submission = new SubmittedAction.SpecialActionSubmission(
                UUID.randomUUID(), Faction.ERASERS, special, null, null, UUID.randomUUID(), UUID.randomUUID(), null);
        try {
            submission.validateFinalEra(era, round, MAX_ERAS);
            submission.validateEarliestRound(era, round, ANNIHILATE_EARLIEST);
            if (SpecialActionRoundRules.declarationWindowOnly(special)
                    || SpecialActionRoundRules.obscureInFinalRound(special, round)
                    || SpecialActionRoundRules.exposeOutsideItsRound(special, round)) {
                return false;
            }
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    @Test
    @DisplayName("rules reject being asked about an unmodelled null faction")
    void nullFactionRejected() {
        assertThatThrownBy(() -> SpecialActionAvailabilityPolicy.evaluate(null, rules(1, 1), fresh()))
                .isInstanceOf(NullPointerException.class);
    }
}
