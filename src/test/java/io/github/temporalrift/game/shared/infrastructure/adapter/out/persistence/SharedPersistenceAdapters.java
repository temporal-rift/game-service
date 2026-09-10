package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The adapter is package-private, so the shared persistence test configuration reaches it through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import(ProcessedEventRepositoryAdapter.class)
public class SharedPersistenceAdapters {}
