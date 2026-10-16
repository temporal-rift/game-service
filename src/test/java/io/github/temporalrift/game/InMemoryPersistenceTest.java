package io.github.temporalrift.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The in-memory counterpart of {@link PersistenceIntegrationTest}: the same adapters over the in-memory store. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(
        classes = InMemoryPersistenceTestConfiguration.class,
        webEnvironment = WebEnvironment.NONE,
        properties = "game.simulation.enabled=true")
@ActiveProfiles({"test", "in-memory"})
@Transactional
public @interface InMemoryPersistenceTest {}
