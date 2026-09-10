package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The adapters are package-private, so the shared persistence test configuration reaches them through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    ActionRoundRepositoryAdapter.class,
    ActionRoundSagaAdapter.class,
    ActivistEraStateRepositoryAdapter.class,
    CurrentEraFutureEventAdapter.class,
    HandSelectionRepositoryAdapter.class,
    ParadoxResolutionPhaseRepositoryAdapter.class,
    PlayerStateRepositoryAdapter.class,
    SpecialActionEraUsageRepositoryAdapter.class
})
public class ActionPersistenceAdapters {}
