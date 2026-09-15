package io.github.temporalrift.game.scoring.domain.playerscore;

import io.github.temporalrift.game.shared.domain.model.Faction;

public enum ScoreReason {
    ANNIHILATED_OUTCOME(Faction.ERASERS),
    CORRUPTED_OPPONENT_CARD(Faction.ERASERS),
    ERA_ENDED_WITH_FEWER_OUTCOMES(Faction.ERASERS),
    EVENT_RESOLVED_AS_WRITTEN(Faction.PROPHETS),
    FULFILLMENT_SUCCEEDED(Faction.PROPHETS),
    EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN(Faction.PROPHETS),
    SECRET_OUTCOME_WON(Faction.REVISIONISTS),
    FACTION_UNIDENTIFIED(Faction.REVISIONISTS),
    MIMIC_CONTRIBUTED_TO_WIN(Faction.REVISIONISTS),
    CHAIN_LINK_ADDED(Faction.WEAVERS),
    CHAIN_COMPLETED(Faction.WEAVERS),
    CHAIN_BROKEN(Faction.WEAVERS),
    DECLARED_OUTCOME_WON_WITH_RALLY(Faction.ACTIVISTS),
    DECLARED_OUTCOME_WON(Faction.ACTIVISTS),
    EXPOSE_CHANGED_PLAYER_BEHAVIOR(Faction.ACTIVISTS),
    PARADOX_CASCADE_PENALTY(null);

    private final Faction faction;

    ScoreReason(Faction faction) {
        this.faction = faction;
    }

    public Faction faction() {
        return faction;
    }

    boolean belongsTo(Faction playerFaction) {
        return faction == null || faction == playerFaction;
    }
}
