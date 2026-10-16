package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class InMemoryProfileGuardTest {

    @Test
    void startupFailsNamingTheSimulationSwitchWhenItIsMissing() {
        assertThatThrownBy(() -> context(false, true))
                .isInstanceOf(InMemoryProfileException.class)
                .hasMessageContaining("game.simulation.enabled");
    }

    @Test
    void startupFailsNamingTheEventSinkWhenTheHostDoesNotSupplyIt() {
        assertThatThrownBy(() -> context(true, false))
                .isInstanceOf(InMemoryProfileException.class)
                .hasMessageContaining("simulationEventSink");
    }

    @Test
    void startupSucceedsWhenBothRequirementsAreMet() {
        try (var context = context(true, true)) {
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    void theAnalyzerExplainsHowToSatisfyEachRequirement() {
        var analyzer = new InMemoryProfileFailureAnalyzer();
        var property = new InMemoryProfileException("game.simulation.enabled", "needs the switch");
        var sink = new InMemoryProfileException("simulationEventSink", "needs the sink");

        assertThat(analyzer.analyze(property, property).getAction()).contains("game.simulation.enabled=true");
        assertThat(analyzer.analyze(sink, sink).getAction()).contains("simulationEventSink");
    }

    private static AnnotationConfigApplicationContext context(boolean simulation, boolean sink) {
        var parent = new AnnotationConfigApplicationContext();
        if (sink) {
            parent.registerBean("simulationEventSink", Consumer.class, () -> (Consumer<Object>) ignored -> {});
        }
        parent.refresh();
        var child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.getEnvironment().setActiveProfiles("in-memory");
        child.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("game.simulation.enabled", simulation)));
        child.register(InMemoryProfileGuard.class);
        child.refresh();
        return child;
    }
}
