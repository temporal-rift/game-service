package io.github.temporalrift.game.session.infrastructure.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CardDealingConfig {

    @Bean
    RandomGenerator cardDealingRandomGenerator() {
        return new SecureRandom();
    }
}
