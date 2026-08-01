package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_faction_identification")
class ScoringFactionIdentificationJpaEntity extends GamePlayerScopedJpaEntity {

    protected ScoringFactionIdentificationJpaEntity() {}
}
