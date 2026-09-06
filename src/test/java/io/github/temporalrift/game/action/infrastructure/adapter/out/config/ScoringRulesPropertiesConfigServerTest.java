package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import io.github.temporalrift.game.shared.CardType;

/**
 * Exercises spring.config.import=configserver:... end to end against an in-process HTTP stub serving the
 * Config Server's real /{application}/{profile} response shape, rather than a shared Docker image from the
 * infrastructure repo (see design.md Decision 3 of the game-scoring-config-client change — mirrors
 * timeline-service's TimelineRulesPropertiesConfigServerTest).
 */
class ScoringRulesPropertiesConfigServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer configServerStub;

    @AfterEach
    void stopStub() {
        if (configServerStub != null) {
            configServerStub.stop(0);
        }
    }

    @Test
    void bindsValuesFromConfigServer_whenNoLocalOverridePresent() throws IOException {
        configServerStub = startStub(completeSource());

        try (var context = startContext(configServerStub)) {
            var props = context.getBean(ScoringRulesProperties.class);
            assertThat(props.cardShift(CardType.PUSH)).isEqualTo(20);
            assertThat(props.cardShift(CardType.SUPPRESS)).isEqualTo(-20);
            assertThat(props.swingShift()).isEqualTo(30);
            assertThat(props.bandLowMaxProbability()).isEqualTo(30);
            assertThat(props.bandMediumMaxProbability()).isEqualTo(60);
        }
    }

    @Test
    void localOverride_winsOverConfigServerValue() throws IOException {
        configServerStub = startStub(completeSource());

        try (var context = startContext(configServerStub, "game.rules.scoring.card-shifts.PUSH=999")) {
            var props = context.getBean(ScoringRulesProperties.class);
            assertThat(props.cardShift(CardType.PUSH)).isEqualTo(999);
            // Every other key still comes from the Config Server stub, untouched by the override.
            assertThat(props.cardShift(CardType.SUPPRESS)).isEqualTo(-20);
            assertThat(props.bandLowMaxProbability()).isEqualTo(30);
        }
    }

    @Test
    void incompleteEffectiveConfiguration_stillFailsFast() throws IOException {
        var incompleteCardShifts = completeSource();
        incompleteCardShifts.remove("game.rules.scoring.card-shifts.JAM");
        configServerStub = startStub(incompleteCardShifts);

        assertThatThrownBy(() -> startContext(configServerStub)).isInstanceOf(RuntimeException.class);
    }

    private static ConfigurableApplicationContext startContext(HttpServer stub, String... localOverrides) {
        // Passed as --key=value command-line args (Spring Boot's highest-priority source) rather than via
        // SpringApplicationBuilder#properties (its lowest-priority default properties source) — the latter
        // would be silently shadowed by this module's own classpath application.yml, which is on the test
        // classpath too and declares its own spring.config.import pointing at the real Compose config-server.
        var baseUrl = "http://localhost:" + stub.getAddress().getPort();
        var args = new ArrayList<String>(List.of(
                // A config-name with no matching classpath file, so this module's own application.yml (and
                // its own, unrelated spring.config.import) is never picked up alongside the stub's import.
                "--spring.config.name=scoring-rules-properties-config-server-test",
                "--spring.application.name=game-service",
                "--spring.config.import=configserver:" + baseUrl,
                "--spring.cloud.config.fail-fast=true"));
        for (var override : localOverrides) {
            args.add("--" + override);
        }
        return new SpringApplicationBuilder(RulesPropertiesConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(args.toArray(String[]::new));
    }

    private static LinkedHashMap<String, Object> completeSource() {
        var source = new LinkedHashMap<String, Object>();
        source.put("game.rules.scoring.card-shifts.PUSH", 20);
        source.put("game.rules.scoring.card-shifts.SUPPRESS", -20);
        for (var cardType : CardType.values()) {
            if (cardType != CardType.PUSH && cardType != CardType.SUPPRESS && cardType != CardType.SWING) {
                source.put("game.rules.scoring.card-shifts." + cardType.name(), 0);
            }
        }
        source.put("game.rules.scoring.swing-shift", 30);
        source.put("game.rules.scoring.low-max-probability", 30);
        source.put("game.rules.scoring.medium-max-probability", 60);
        return source;
    }

    private static HttpServer startStub(Map<String, Object> source) throws IOException {
        var responseBody = configServerResponseJson(source);
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private static String configServerResponseJson(Map<String, Object> source) throws IOException {
        var body = Map.of(
                "name", "game-service",
                "profiles", List.of("default"),
                "propertySources", List.of(Map.of("name", "test-stub", "source", source)));
        return JSON.writeValueAsString(body);
    }

    @Configuration
    @EnableConfigurationProperties(ScoringRulesProperties.class)
    static class RulesPropertiesConfiguration {}
}
