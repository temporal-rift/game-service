package io.github.temporalrift.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class InMemoryRuntimeIT {

    private final List<ConfigurableApplicationContext> contexts = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeContexts() {
        contexts.forEach(ConfigurableApplicationContext::close);
    }

    @Test
    void startsWithoutExternalInfrastructureAndServesRestOnAnEphemeralPort() throws Exception {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");

        var port = ((WebServerApplicationContext) application).getWebServer().getPort();
        var response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(port).isPositive();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(application.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(application.getBeansOfType(KafkaAdmin.class)).isEmpty();
        assertThat(application.containsBean("simulationEventDelivery")).isTrue();
    }

    @Test
    void failsToStartWithoutTheSimulationSwitch() {
        var host = host(new CopyOnWriteArrayList<>());

        assertThatThrownBy(() -> start(host)).hasMessageContaining("game.simulation.enabled");
    }

    @Test
    void failsToStartWithoutTheEventSink() {
        var host = new AnnotationConfigApplicationContext();
        host.refresh();
        contexts.add(host);

        assertThatThrownBy(() -> start(host, "game.simulation.enabled=true"))
                .hasMessageContaining("simulationEventSink");
    }

    private ConfigurableApplicationContext start(ConfigurableApplicationContext host, String... properties) {
        var application = new SpringApplicationBuilder(GameServiceApplication.class)
                .parent(host)
                .profiles("in-memory", "test")
                .properties("server.port=0", "spring.main.banner-mode=off")
                .properties(properties)
                .run();
        contexts.add(application);
        return application;
    }

    private ConfigurableApplicationContext host(List<Map<String, Object>> sink) {
        var host = new AnnotationConfigApplicationContext();
        host.registerBean("simulationEventSink", Consumer.class, () -> (Consumer<Map<String, Object>>) sink::add);
        host.registerBean(
                JwtDecoder.class,
                () -> token -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token)
                        .build());
        host.refresh();
        contexts.add(host);
        return host;
    }
}
