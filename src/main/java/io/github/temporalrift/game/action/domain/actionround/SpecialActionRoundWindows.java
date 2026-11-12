package io.github.temporalrift.game.action.domain.actionround;

/**
 * The configured action-round windows of the round-limited specials: Annihilate is playable from its earliest round
 * onward, Rewrite only up to its latest round. Submission validation and the round-open availability report read the
 * same value.
 *
 * @param annihilateEarliestRound first action round (1 to 3) in which Annihilate is accepted
 * @param rewriteLatestRound last action round (1 to 3) in which Rewrite is accepted
 */
public record SpecialActionRoundWindows(int annihilateEarliestRound, int rewriteLatestRound) {}
