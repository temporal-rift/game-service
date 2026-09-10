package io.github.temporalrift.game;

import java.time.Clock;

import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.game.action.infrastructure.adapter.out.persistence.ActionPersistenceAdapters;
import io.github.temporalrift.game.scoring.application.command.AwardUnidentifiedFactionScores;
import io.github.temporalrift.game.scoring.application.query.PlayerScoreQueryService;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence.ScoringPersistenceAdapters;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.infrastructure.adapter.out.persistence.SessionPersistenceAdapters;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.SharedPersistenceAdapters;

/**
 * Everything the persistence slice needs, in one place: a context is cached per distinct configuration, and
 * each new one starts a container and replays the changelog, so a per-test import list costs a full slice
 * boot. Register new persistence adapters in their module's adapters configuration rather than in a test.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    PostgresTestcontainersConfiguration.class,
    JacksonAutoConfiguration.class,
    ActionPersistenceAdapters.class,
    ScoringPersistenceAdapters.class,
    SessionPersistenceAdapters.class,
    SharedPersistenceAdapters.class,
    // Application services whose transaction boundaries are only observable against a real database.
    AwardUnidentifiedFactionScores.class,
    PlayerScoreQueryService.class
})
public class PersistenceTestConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Outbox publication is covered by its own integration test against the real adapter. */
    @Bean
    SessionEventPublisher sessionEventPublisher() {
        return event -> {};
    }
}
