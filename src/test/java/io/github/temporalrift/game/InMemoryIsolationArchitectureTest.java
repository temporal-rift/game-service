package io.github.temporalrift.game;

import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Two in-memory application instances share a JVM, so their classes may not keep state in statics. */
@AnalyzeClasses(packages = "io.github.temporalrift.game", importOptions = ImportOption.DoNotIncludeTests.class)
class InMemoryIsolationArchitectureTest {

    @ArchTest
    static final ArchRule in_memory_classes_have_no_mutable_static_fields = noFields()
            .that()
            .areStatic()
            .and()
            .areNotFinal()
            .and()
            .areDeclaredInClassesThat(or(
                    resideInAPackage("..adapter.out.memory.."),
                    simpleNameStartingWith("InMemory"),
                    simpleNameStartingWith("Simulation"),
                    simpleNameStartingWith("KafkaListenerRegistry")))
            .should()
            .beStatic()
            .allowEmptyShould(true)
            .as("In-memory classes must not keep mutable static state");

    @ArchTest
    static final ArchRule in_memory_classes_have_no_static_collections_or_counters = noFields()
            .that()
            .areStatic()
            .and()
            .haveRawType(or(
                    assignableTo(Collection.class),
                    assignableTo(Map.class),
                    assignableTo(AtomicInteger.class),
                    assignableTo(AtomicLong.class)))
            .and()
            .areDeclaredInClassesThat(or(
                    resideInAPackage("..adapter.out.memory.."),
                    simpleNameStartingWith("InMemory"),
                    simpleNameStartingWith("Simulation"),
                    simpleNameStartingWith("KafkaListenerRegistry")))
            .should()
            .beStatic()
            .allowEmptyShould(true)
            .as("In-memory classes must not share collections or counters through statics");
}
