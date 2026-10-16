package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Marks an in-memory repository that takes the place of its Spring Data counterpart under the in-memory profile. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Profile("in-memory")
public @interface InMemoryRepository {}
