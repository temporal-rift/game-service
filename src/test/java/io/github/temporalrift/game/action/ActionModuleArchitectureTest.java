package io.github.temporalrift.game.action;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;

@AnalyzeClasses(packages = "io.github.temporalrift.game.action", importOptions = ImportOption.DoNotIncludeTests.class)
public class ActionModuleArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_or_persistence = noClasses()
            .that()
            .resideInAPackage("io.github.temporalrift.game.action.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "lombok..")
            .as("Action domain layer must be plain Java — no Spring, JPA, or Lombok");

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("io.github.temporalrift.game.action.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.github.temporalrift.game.action.infrastructure..")
            .as("Action application layer must not depend on infrastructure");

    @ArchTest
    static final ArchRule driven_ports_must_only_be_implemented_in_infrastructure = noClasses()
            .that()
            .resideOutsideOfPackage("io.github.temporalrift.game.action.infrastructure.adapter.out..")
            .should()
            .implement(resideInAPackage("io.github.temporalrift.game.action.domain.port.out.."))
            .as("Driven ports must only be implemented in action infrastructure.adapter.out");

    @ArchTest
    static final ArchRule action_must_not_use_field_injection = noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("io.github.temporalrift.game.action..")
            .should()
            .beAnnotatedWith(Autowired.class)
            .as("Action module must use constructor injection — never @Autowired on fields");

    @ArchTest
    static final ArchRule no_cross_module_internal_dependency = noClasses()
            .that()
            .resideInAPackage("io.github.temporalrift.game.action..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.github.temporalrift.game.session.application..",
                    "io.github.temporalrift.game.session.domain..",
                    "io.github.temporalrift.game.session.infrastructure..",
                    "io.github.temporalrift.game.scoring.application..",
                    "io.github.temporalrift.game.scoring.domain..",
                    "io.github.temporalrift.game.scoring.infrastructure..",
                    "io.github.temporalrift.game.shared.infrastructure..")
            .as("Action module must not depend on session, scoring, or shared internals — use published API only");

    @ArchTest
    static final ArchRule no_bare_event_listener_annotations = methods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("io.github.temporalrift.game.action..")
            .should(notUseBareEventListener())
            .as("Action module must use @ApplicationModuleListener — never bare @EventListener");

    @ArchTest
    static final ArchRule action_must_not_publish_directly_to_kafka = noClasses()
            .that()
            .resideInAPackage("io.github.temporalrift.game.action..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(KafkaTemplate.class)
            .as("Action module must publish through ActionEventPublisher, not KafkaTemplate");

    @ArchTest
    static final ArchRule rest_adapters_must_not_depend_on_concrete_application_implementations = noClasses()
            .that()
            .resideInAPackage("io.github.temporalrift.game.action.infrastructure.adapter.in.rest..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.github.temporalrift.game.action.application.command..",
                    "io.github.temporalrift.game.action.application.query..",
                    "io.github.temporalrift.game.action.application.saga..")
            .as("Action REST adapters must depend on application.port.in use cases, not concrete handlers or sagas");

    @ArchTest
    static final ArchRule action_domain_and_application_must_not_depend_on_configuration_properties = noClasses()
            .that()
            .resideInAnyPackage(
                    "io.github.temporalrift.game.action.domain..", "io.github.temporalrift.game.action.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.github.temporalrift.game.session.infrastructure.adapter.out.config..")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.boot.context.properties..")
            .as("Action domain/application code must access configuration through ports such as GameRulesPort");

    private static ArchCondition<JavaMethod> notUseBareEventListener() {
        return new ArchCondition<>("not use bare @EventListener") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                var annotationTypeNames = method.getAnnotations().stream()
                        .map(annotation -> annotation.getRawType().getName())
                        .toList();

                var hasBareEventListener = annotationTypeNames.contains(EventListener.class.getName())
                        && !annotationTypeNames.contains(ApplicationModuleListener.class.getName());

                if (hasBareEventListener) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " must use @ApplicationModuleListener instead of @EventListener"));
                }
            }
        };
    }
}
