package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;

@ConfigurationProperties("game.rules")
@Validated
public record SessionRulesProperties(
        @Min(2) int minPlayers,
        @Min(2) int maxPlayers,
        @Min(1) int maxEras,
        @Min(1) int maxCascadedParadoxes,
        @Min(1) int eventsPerEra,
        @Min(1) int cardsPerHand,
        @Min(1) int winScoreThreshold,
        @NotEmpty Map<Integer, Integer> actionRoundTimerSeconds)
        implements GameRulesPort {

    private static final int DEFAULT_ACTION_ROUND_TIMER_SECONDS = 60;

    @Override
    public int actionRoundTimerSeconds(int playerCount) {
        return actionRoundTimerSeconds.getOrDefault(playerCount, DEFAULT_ACTION_ROUND_TIMER_SECONDS);
    }
}
