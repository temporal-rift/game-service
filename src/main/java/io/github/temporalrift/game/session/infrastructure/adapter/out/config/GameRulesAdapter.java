package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.infrastructure.config.SessionRulesProperties;

@Component
public class GameRulesAdapter implements GameRulesPort {

    private final SessionRulesProperties sessionRulesProperties;

    public GameRulesAdapter(SessionRulesProperties sessionRulesProperties) {
        this.sessionRulesProperties = sessionRulesProperties;
    }

    @Override
    public int minPlayers() {
        return sessionRulesProperties.minPlayers();
    }

    @Override
    public int maxPlayers() {
        return sessionRulesProperties.maxPlayers();
    }

    @Override
    public int maxEras() {
        return sessionRulesProperties.maxEras();
    }

    @Override
    public int maxCascadedParadoxes() {
        return sessionRulesProperties.maxCascadedParadoxes();
    }

    @Override
    public int eventsPerEra() {
        return sessionRulesProperties.eventsPerEra();
    }

    @Override
    public int actionRoundTimerSeconds(int playerCount) {
        return sessionRulesProperties.actionRoundTimerSeconds().getOrDefault(playerCount, 60);
    }
}
