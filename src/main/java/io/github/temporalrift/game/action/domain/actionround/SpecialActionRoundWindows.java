package io.github.temporalrift.game.action.domain.actionround;

/**
 * The configured action-round windows of the round-limited specials: Annihilate is playable from its earliest round
 * onward, every faction bet (Thread, Reweave, Rewrite, Fulfillment) only up to the faction-bet latest round.
 * Submission validation and the round-open availability report read the same value.
 *
 * @param annihilateEarliestRound first action round (1 to 3) in which Annihilate is accepted
 * @param factionBetLatestRound last action round (1 to 3) in which a faction bet is accepted
 */
public record SpecialActionRoundWindows(int annihilateEarliestRound, int factionBetLatestRound) {}
