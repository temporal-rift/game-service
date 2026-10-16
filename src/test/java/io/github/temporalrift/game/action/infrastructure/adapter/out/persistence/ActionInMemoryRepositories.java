package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;

/** The in-memory repositories are package-private, so the in-memory test configuration reaches them through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import(InMemoryActionExecutionProbe.class)
@ComponentScan(
        basePackageClasses = ActionInMemoryRepositories.class,
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.ANNOTATION, classes = InMemoryRepository.class))
public class ActionInMemoryRepositories {}
