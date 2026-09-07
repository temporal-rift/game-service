package io.github.temporalrift.game.action.domain.specialactionerausage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.SpecialAction;

class SpecialActionEraUsageTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final int ERA_NUMBER = 1;

    @Test
    void claim_firstUseOfASpecial_succeeds() {
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), GAME_ID, ERA_NUMBER, PLAYER_ID);

        usage.claim(SpecialAction.ANNIHILATE);

        assertThat(usage.claimedSpecials()).containsExactly(SpecialAction.ANNIHILATE);
    }

    @Test
    void claim_secondUseOfTheSameSpecial_throws() {
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), GAME_ID, ERA_NUMBER, PLAYER_ID);
        usage.claim(SpecialAction.ANNIHILATE);

        assertThatThrownBy(() -> usage.claim(SpecialAction.ANNIHILATE))
                .isInstanceOf(SpecialActionEraBudgetExhaustedException.class);
    }

    @Test
    void claim_differentSpecialInTheSameEra_succeedsIndependently() {
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), GAME_ID, ERA_NUMBER, PLAYER_ID);
        usage.claim(SpecialAction.ANNIHILATE);

        usage.claim(SpecialAction.CORRUPT);

        assertThat(usage.claimedSpecials()).containsExactlyInAnyOrder(SpecialAction.ANNIHILATE, SpecialAction.CORRUPT);
    }

    @Test
    void claim_rejectedAttemptLeavesTheClaimSetUnchanged() {
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), GAME_ID, ERA_NUMBER, PLAYER_ID);
        usage.claim(SpecialAction.ANNIHILATE);

        assertThatThrownBy(() -> usage.claim(SpecialAction.ANNIHILATE))
                .isInstanceOf(SpecialActionEraBudgetExhaustedException.class);

        assertThat(usage.claimedSpecials()).containsExactly(SpecialAction.ANNIHILATE);
    }

    @Test
    void reconstitute_restoresPreviouslyClaimedSpecials() {
        Set<SpecialAction> claimed = EnumSet.of(SpecialAction.SEAL);
        var usage = SpecialActionEraUsage.reconstitute(UUID.randomUUID(), GAME_ID, ERA_NUMBER, PLAYER_ID, claimed);

        assertThatThrownBy(() -> usage.claim(SpecialAction.SEAL))
                .isInstanceOf(SpecialActionEraBudgetExhaustedException.class);
    }
}
