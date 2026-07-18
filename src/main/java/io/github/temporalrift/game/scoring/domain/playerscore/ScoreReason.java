package io.github.temporalrift.game.scoring.domain.playerscore;

import io.github.temporalrift.game.shared.Faction;

public enum ScoreReason {
    ANNIHILATED_OUTCOME(Faction.ERASERS, 3),
    CORRUPTED_OPPONENT_CARD(Faction.ERASERS, 2),
    ERA_ENDED_WITH_FEWER_OUTCOMES(Faction.ERASERS, 5),
    EVENT_RESOLVED_AS_WRITTEN(Faction.PROPHETS, 4),
    FULFILLMENT_SUCCEEDED(Faction.PROPHETS, 8),
    EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN(Faction.PROPHETS, -2),
    SECRET_OUTCOME_WON(Faction.REVISIONISTS, 4),
    FACTION_UNIDENTIFIED(Faction.REVISIONISTS, 6),
    MIMIC_CONTRIBUTED_TO_WIN(Faction.REVISIONISTS, 2),
    CHAIN_LINK_ADDED(Faction.WEAVERS, 2),
    CHAIN_COMPLETED(Faction.WEAVERS, 10),
    CHAIN_BROKEN(Faction.WEAVERS, -3),
    DECLARED_OUTCOME_WON_WITH_RALLY(Faction.ACTIVISTS, 8),
    DECLARED_OUTCOME_WON(Faction.ACTIVISTS, 4),
    EXPOSE_CHANGED_PLAYER_BEHAVIOR(Faction.ACTIVISTS, 2);

    private final Faction faction;
    private final int pointsDelta;

    ScoreReason(Faction faction, int pointsDelta) {
        this.faction = faction;
        this.pointsDelta = pointsDelta;
    }

    public Faction faction() {
        return faction;
    }

    public int pointsDelta() {
        return pointsDelta;
    }

    boolean belongsTo(Faction playerFaction) {
        return faction == playerFaction;
    }
}
