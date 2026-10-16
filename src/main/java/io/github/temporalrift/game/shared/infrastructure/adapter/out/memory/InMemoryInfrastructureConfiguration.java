package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/** The storage, transactions and event publication registry that replace PostgreSQL under the in-memory profile. */
@Configuration(proxyBeanMethods = false)
@Profile("in-memory")
class InMemoryInfrastructureConfiguration {

    @Bean
    InMemoryDatabase inMemoryDatabase(@Value("${game.simulation.in-memory.lock-timeout:30s}") String lockTimeout) {
        return new InMemoryDatabase(DurationStyle.detectAndParse(lockTimeout));
    }

    @Bean
    PlatformTransactionManager transactionManager(InMemoryDatabase database) {
        return new InMemoryTransactionManager(database);
    }
}
