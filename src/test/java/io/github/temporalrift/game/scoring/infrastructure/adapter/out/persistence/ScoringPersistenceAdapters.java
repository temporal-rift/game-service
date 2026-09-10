package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The adapters are package-private, so the shared persistence test configuration reaches them through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    EndGameScoreFactRepositoryAdapter.class,
    EraScoringContextRepositoryAdapter.class,
    FactionIdentificationRepositoryAdapter.class,
    PlayerScoreRepositoryAdapter.class,
    ScoringEraCompletionRepositoryAdapter.class,
    TimelineOutcomeInboxRepositoryAdapter.class
})
public class ScoringPersistenceAdapters {}
