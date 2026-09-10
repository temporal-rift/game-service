package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The adapters are package-private, so the shared persistence test configuration reaches them through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    EraSagaAdapter.class,
    EraSagaScoresUpdatedInboxRepositoryAdapter.class,
    GameRepositoryAdapter.class,
    LobbyRepositoryAdapter.class
})
public class SessionPersistenceAdapters {}
