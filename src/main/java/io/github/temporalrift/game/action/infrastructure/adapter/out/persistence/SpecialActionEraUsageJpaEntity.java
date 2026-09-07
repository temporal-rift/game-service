package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "special_action_era_usage")
class SpecialActionEraUsageJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "claimed_specials", columnDefinition = "text[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> claimedSpecials;

    protected SpecialActionEraUsageJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    List<String> getClaimedSpecials() {
        return claimedSpecials;
    }

    void setClaimedSpecials(List<String> claimedSpecials) {
        this.claimedSpecials = claimedSpecials;
    }
}
