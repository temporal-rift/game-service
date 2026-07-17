package io.github.temporalrift.game;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.shared.RestAdviceOrder;

@AnalyzeClasses(packages = "io.github.temporalrift.game", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_or_persistence = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "lombok..")
            .as("Domain layer must be plain Java — no Spring, JPA, or Lombok");

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .as("Application layer must not depend on infrastructure");

    @ArchTest
    static final ArchRule driven_ports_must_only_be_implemented_in_infrastructure = noClasses()
            .that()
            .resideOutsideOfPackage("..infrastructure.adapter.out..")
            .should()
            .implement(resideInAPackage("..domain.port.out.."))
            .as("Driven ports must only be implemented in infrastructure.adapter.out");

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .as("Use constructor injection — never @Autowired on fields");

    // Advice order — not exception-type specificity — decides handler lookup across advices. An
    // advice without an explicit @Order ties with the shared fallback and may be silently
    // overshadowed by its Exception catch-all, turning that module's 4xx mappings into 500s.
    @ArchTest
    static final ArchRule rest_advices_must_declare_explicit_order = classes()
            .that()
            .areAnnotatedWith(RestControllerAdvice.class)
            .should(declareExplicitAdviceOrder())
            .as("Every @RestControllerAdvice must declare @Order(RestAdviceOrder.MODULE); "
                    + "only the shared fallback uses RestAdviceOrder.GLOBAL_FALLBACK");

    private static ArchCondition<JavaClass> declareExplicitAdviceOrder() {
        return new ArchCondition<>("declare an explicit advice order above the global fallback") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!javaClass.isAnnotatedWith(Order.class)) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " lacks @Order(RestAdviceOrder.MODULE)"));
                    return;
                }
                var order = javaClass.getAnnotationOfType(Order.class).value();
                var isSharedFallback = javaClass.getPackageName().contains(".shared.");
                if (!isSharedFallback && order >= RestAdviceOrder.GLOBAL_FALLBACK) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " must outrank the global fallback — use RestAdviceOrder.MODULE"));
                }
            }
        };
    }
}
