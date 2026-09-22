package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class GameEraScopedJpaEntity extends GameScopedJpaEntity {

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    public int getEraNumber() {
        return eraNumber;
    }

    public void setEraNumber(int eraNumber) {
        this.eraNumber = eraNumber;
    }
}
