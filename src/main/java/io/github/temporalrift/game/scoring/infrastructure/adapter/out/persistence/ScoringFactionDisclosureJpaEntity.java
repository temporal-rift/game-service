package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GamePlayerScopedJpaEntity;

@Entity
@Table(name = "scoring_faction_disclosure")
class ScoringFactionDisclosureJpaEntity extends GamePlayerScopedJpaEntity {

    protected ScoringFactionDisclosureJpaEntity() {}
}
