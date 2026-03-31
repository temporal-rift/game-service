package com.temporalrift.game.session.infrastructure.adapter.out.config;

import org.springframework.stereotype.Component;

import com.temporalrift.game.session.domain.port.out.GameRulesPort;
import com.temporalrift.game.session.infrastructure.config.SessionRulesProperties;

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
}
