package io.github.temporalrift.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.sql.DataSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;
import io.github.temporalrift.game.shared.infrastructure.config.ExecutionRulesFixture;
import io.github.temporalrift.game.simulation.application.port.in.AdvanceSimulationClockUseCase;
import io.github.temporalrift.game.simulation.application.port.in.CloseSimulationExecutionUseCase;
import io.github.temporalrift.game.simulation.application.port.in.ConfigureSimulationExecutionUseCase;
import io.github.temporalrift.game.simulation.application.port.in.GetSimulationCheckpointUseCase;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;
import io.github.temporalrift.game.simulation.domain.execution.EffectiveRules;
import io.github.temporalrift.game.simulation.domain.execution.EntropyVersion;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContext;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextConflictException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionState;
import io.github.temporalrift.game.simulation.domain.execution.InvalidExecutionContextException;
import io.github.temporalrift.game.simulation.domain.execution.Seed;
import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;
import io.github.temporalrift.game.simulation.domain.port.out.SourceWatermarks;

class InMemoryRuntimeIT {

    private static final Set<String> WIRE_HEADERS =
            Set.of("eventType", "eventId", "aggregateId", "aggregateType", "gameId", "occurredAt", "version");
    private static final List<UUID> PLAYERS = List.of(
            ExecutionContextTestData.PLAYER_0, ExecutionContextTestData.PLAYER_1, ExecutionContextTestData.PLAYER_2);

    private final List<ConfigurableApplicationContext> contexts = new CopyOnWriteArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void closeContexts() {
        contexts.forEach(ConfigurableApplicationContext::close);
    }

    @Test
    void startsWithoutExternalInfrastructureAndServesRestOnAnEphemeralPort() throws Exception {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");

        var response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl(application) + "/actuator/health"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(port(application)).isPositive();
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

    @Test
    void aGameStartedThroughRestPublishesKafkaShapedRecordsToTheSinkInOrder() throws Exception {
        var sink = new CopyOnWriteArrayList<Map<String, Object>>();
        var application = startConfigured(sink);

        var gameId = startGame(application);

        await().atMost(Duration.ofSeconds(20))
                .until(() -> eventTypes(sink, gameId).contains("GameStarted"));
        var objectMapper = application.getBean(ObjectMapper.class);
        sink.forEach(message -> {
            assertThat(message.keySet()).containsExactly("topic", "key", "headers", "payload");
            assertThat(message).containsEntry("topic", "game.events");
            @SuppressWarnings("unchecked")
            var headers = (Map<String, String>) message.get("headers");
            assertThat(headers.keySet()).isEqualTo(WIRE_HEADERS);
            assertThat(headers).containsEntry("gameId", (String) message.get("key"));
            assertThat(objectMapper.readTree((byte[]) message.get("payload")).isObject())
                    .isTrue();
        });
        var lifecycle = eventTypes(sink, gameId);
        assertThat(lifecycle.indexOf("LobbyCreated")).isLessThan(lifecycle.indexOf("GameStarted"));
    }

    @Test
    void aDeliveredTimelineRecordRunsEveryConsumerGroupOnceAndIsIdempotentOnRedelivery() throws Exception {
        var application = startConfigured(new CopyOnWriteArrayList<>());
        var delivery = application.getBean("simulationEventDelivery", Consumer.class);
        var gameId = startGame(application);
        var message = eraResolutionCompleted(application, gameId, UUID.randomUUID());
        var sessionLog = captureLogs("EraResolutionCompletedKafkaConsumer");

        deliver(delivery, message);

        var scoring = application.getBean(EraScoringContextRepository.class);
        assertThat(inTransaction(application, () -> scoring.eraResolutionCompleted(gameId, 1)))
                .isTrue();
        var eventId = UUID.fromString(headers(message).get("eventId"));
        assertThat(inTransaction(
                        application,
                        () -> application
                                .getBean(ProcessedEventRepository.class)
                                .tryMarkProcessed(eventId, "session.era-resolution-completed")))
                .as("the session consumer claimed the record")
                .isFalse();
        assertThat(timelinePositions(application))
                .allSatisfy(offset -> assertThat(offset).isEqualTo(1L));
        assertThat(sessionLog.list)
                .noneMatch(event -> event.getFormattedMessage().contains("Duplicate"));

        deliver(delivery, message);

        assertThat(sessionLog.list)
                .anyMatch(event -> event.getFormattedMessage().contains("Duplicate"));
        assertThat(timelinePositions(application))
                .allSatisfy(offset -> assertThat(offset).isEqualTo(2L));
    }

    @Test
    void aRecordForATopicNobodyConsumesIsIgnored() throws Exception {
        var application = startConfigured(new CopyOnWriteArrayList<>());
        var delivery = application.getBean("simulationEventDelivery", Consumer.class);
        var before = application.getBean(SourceWatermarks.class).current();
        var message = new LinkedHashMap<>(eraResolutionCompleted(application, UUID.randomUUID(), UUID.randomUUID()));
        message.put("topic", "game.events");

        deliver(delivery, message);

        assertThat(application.getBean(SourceWatermarks.class).current()).isEqualTo(before);
    }

    @Test
    void twoInstancesInOneJvmShareNoState() throws Exception {
        var firstSink = new CopyOnWriteArrayList<Map<String, Object>>();
        var secondSink = new CopyOnWriteArrayList<Map<String, Object>>();
        var first = startConfigured(firstSink);
        var second = startConfigured(secondSink);

        var gameId = startGame(first);
        await().atMost(Duration.ofSeconds(20))
                .until(() -> eventTypes(firstSink, gameId).contains("GameStarted"));
        deliver(
                first.getBean("simulationEventDelivery", Consumer.class),
                eraResolutionCompleted(first, gameId, UUID.randomUUID()));

        assertThat(inTransaction(
                        second, () -> second.getBean(GameRepository.class).findById(gameId)))
                .isEmpty();
        assertThat(secondSink).isEmpty();
        assertThat(timelinePositions(second))
                .allSatisfy(offset -> assertThat(offset).isZero());
        assertThat(timelinePositions(first))
                .allSatisfy(offset -> assertThat(offset).isEqualTo(1L));
    }

    @Test
    void threeConsecutiveGamesOnOneInstanceEachEndOrNotByTheirOwnRules() throws Exception {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");
        var delivery = application.getBean("simulationEventDelivery", Consumer.class);
        var checkpoints = application.getBean(GetSimulationCheckpointUseCase.class);
        var states = new ArrayList<ExecutionState>();

        for (var collapseAfter : List.of(1, 3, 1)) {
            var context = contextWith(
                    UUID.randomUUID(),
                    ExecutionRulesFixture.rulesWith("game.rules.max-cascaded-paradoxes", collapseAfter));
            application.getBean(ConfigureSimulationExecutionUseCase.class).handle(context);
            assertThat(application.getBean(SessionGameRulesPort.class).maxCascadedParadoxes())
                    .isEqualTo(collapseAfter);
            var gameId = startGame(application);
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> checkpoints.handle().drained());

            deliver(delivery, cascadedEraResolution(application, gameId));
            // The synthetic resolution leaves the scoring barrier open, so the lane never drains; it is closed as is.
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> checkpoints.handle().outboxPending() == 0);

            var checkpoint = checkpoints.handle();
            assertThat(checkpoint.gameId()).isEqualTo(gameId);
            assertThat(checkpoint.rulesDigest()).isEqualTo(context.rulesDigest());
            states.add(checkpoint.state());
            application.getBean(CloseSimulationExecutionUseCase.class).handle(context.caseKey());
        }

        assertThat(states).containsExactly(ExecutionState.TERMINAL, ExecutionState.ACTIVE, ExecutionState.TERMINAL);
    }

    @Test
    void aClosedContextLeavesNothingBehindAndItsLateEventsRunNoHandler() throws Exception {
        var sink = new CopyOnWriteArrayList<Map<String, Object>>();
        var application = start(host(sink), "game.simulation.enabled=true");
        var checkpoints = application.getBean(GetSimulationCheckpointUseCase.class);
        var first = contextWith(UUID.randomUUID(), ExecutionRulesFixture.rules());
        application.getBean(ConfigureSimulationExecutionUseCase.class).handle(first);
        var closedGame = startGame(application);
        await().atMost(Duration.ofSeconds(20)).until(() -> checkpoints.handle().drained());
        assertThat(checkpoints.handle().nextDeadline())
                .as("the game has a timer pending")
                .isNotNull();
        application.getBean(CloseSimulationExecutionUseCase.class).handle(first.caseKey());
        var published = sink.size();
        var second =
                contextWith(UUID.randomUUID(), ExecutionRulesFixture.rulesWith("game.rules.win-score-threshold", 22));

        var opened =
                application.getBean(ConfigureSimulationExecutionUseCase.class).handle(second);
        application
                .getBean(AdvanceSimulationClockUseCase.class)
                .handle(new ClockAdvance(UUID.randomUUID(), 0, ExecutionContextTestData.START.plusSeconds(600)));
        var late = eraResolutionCompleted(application, closedGame, UUID.randomUUID());
        var positionsBefore = timelinePositions(application);
        deliver(application.getBean("simulationEventDelivery", Consumer.class), late);

        assertThat(opened.state()).isEqualTo(ExecutionState.READY);
        assertThat(opened.gameId()).isNull();
        assertThat(opened.revision()).isZero();
        assertThat(opened.rulesDigest()).isEqualTo(second.rulesDigest());
        // The close announcement itself is an outbox record until the module listeners have handled it.
        await().atMost(Duration.ofSeconds(20)).until(() -> checkpoints.handle().outboxPending() == 0);
        var checkpoint = checkpoints.handle();
        assertThat(checkpoint.dueTimersPending()).isZero();
        assertThat(checkpoint.gameId()).isNull();
        assertThat(sink).as("nothing of the closed game acted after the close").hasSize(published);
        assertThat(application.getBean(SessionGameRulesPort.class).winScoreThreshold())
                .isEqualTo(22);
        assertThat(inTransaction(
                        application,
                        () -> application.getBean(GameRepository.class).findById(closedGame)))
                .isEmpty();
        var eventId = UUID.fromString(headers(late).get("eventId"));
        assertThat(inTransaction(
                        application,
                        () -> application
                                .getBean(ProcessedEventRepository.class)
                                .tryMarkProcessed(eventId, "session.era-resolution-completed")))
                .as("no consumer claimed the late event")
                .isTrue();
        assertThat(timelinePositions(application))
                .containsExactlyElementsOf(
                        positionsBefore.stream().map(offset -> offset + 1).toList());
    }

    @Test
    void aPlayersRequestCountStartsAgainInTheNextContext() throws Exception {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");
        var configure = application.getBean(ConfigureSimulationExecutionUseCase.class);
        var limit =
                application.getEnvironment().getRequiredProperty("game.rate-limit.requests-per-minute", Integer.class);
        var first = contextWith(UUID.randomUUID(), ExecutionRulesFixture.rules());
        configure.handle(first);
        for (var request = 0; request < limit; request++) {
            assertThat(lobbyStatus(application, PLAYERS.get(0))).isNotEqualTo(429);
        }
        assertThat(lobbyStatus(application, PLAYERS.get(0)))
                .as("the limit holds within a context")
                .isEqualTo(429);

        application.getBean(CloseSimulationExecutionUseCase.class).handle(first.caseKey());
        configure.handle(contextWith(UUID.randomUUID(), ExecutionRulesFixture.rules()));

        for (var request = 0; request < limit; request++) {
            assertThat(lobbyStatus(application, PLAYERS.get(0)))
                    .as("request %d of the next context, at the same logical time", request + 1)
                    .isNotEqualTo(429);
        }
    }

    @Test
    void closeAndOpenFollowThePublishedConflictRules() {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");
        var configure = application.getBean(ConfigureSimulationExecutionUseCase.class);
        var close = application.getBean(CloseSimulationExecutionUseCase.class);
        var first = contextWith(UUID.randomUUID(), ExecutionRulesFixture.rules());
        var other = contextWith(UUID.randomUUID(), ExecutionRulesFixture.rules());
        var unrelated = UUID.randomUUID();
        var firstKey = first.caseKey();

        assertThatThrownBy(() -> close.handle(firstKey)).isInstanceOf(ExecutionNotConfiguredException.class);
        configure.handle(first);
        configure.handle(first);
        assertThatThrownBy(() -> configure.handle(other)).isInstanceOf(ExecutionContextConflictException.class);
        assertThatThrownBy(() -> close.handle(unrelated)).isInstanceOf(ExecutionContextConflictException.class);

        close.handle(firstKey);
        close.handle(firstKey);
        assertThatThrownBy(() -> close.handle(unrelated)).isInstanceOf(ExecutionContextConflictException.class);
        assertThatThrownBy(application.getBean(GetSimulationCheckpointUseCase.class)::handle)
                .isInstanceOf(ExecutionNotConfiguredException.class);
        configure.handle(other);
        close.handle(firstKey);
    }

    @Test
    void aContextWhoseRulesLackAUsedKeyOrBreakARuleIsRejectedByThatKey() {
        var application = start(host(new CopyOnWriteArrayList<>()), "game.simulation.enabled=true");
        var configure = application.getBean(ConfigureSimulationExecutionUseCase.class);
        var missing = contextWith(UUID.randomUUID(), ExecutionRulesFixture.rulesWithout("game.rules.cards-per-hand"));
        var dealTooSmall =
                contextWith(UUID.randomUUID(), ExecutionRulesFixture.rulesWith("game.rules.cards-per-deal", 4));

        assertThatThrownBy(() -> configure.handle(missing))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("game.rules.cards-per-hand");
        assertThatThrownBy(() -> configure.handle(dealTooSmall))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("cards-per-deal");
        assertThatThrownBy(application.getBean(GetSimulationCheckpointUseCase.class)::handle)
                .isInstanceOf(ExecutionNotConfiguredException.class);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static ExecutionContext contextWith(UUID caseKey, Map<String, Object> rules) {
        var effective = new EffectiveRules(rules);
        return new ExecutionContext(
                caseKey,
                new Seed("42"),
                EntropyVersion.SHA256_V1,
                ExecutionContextTestData.MANIFEST_DIGEST,
                effective,
                effective.digest(),
                ExecutionContextTestData.START,
                ExecutionContextTestData.threeSeats());
    }

    private static Map<String, Object> cascadedEraResolution(ConfigurableApplicationContext application, UUID gameId) {
        var message = new LinkedHashMap<>(eraResolutionCompleted(application, gameId, UUID.randomUUID()));
        var mapper = application.getBean(ObjectMapper.class);
        message.put(
                "payload",
                mapper.writeValueAsBytes(new EraResolutionCompletedPayload(
                        gameId, 1, List.of(new EraTerminalResolution(UUID.randomUUID(), 0, "CASCADED", null)))));
        return message;
    }

    private ConfigurableApplicationContext startConfigured(List<Map<String, Object>> sink) {
        var application = start(host(sink), "game.simulation.enabled=true");
        application
                .getBean(ConfigureSimulationExecutionUseCase.class)
                .handle(contextWith(ExecutionContextTestData.CASE_KEY, ExecutionRulesFixture.rules()));
        return application;
    }

    private UUID startGame(ConfigurableApplicationContext application) throws Exception {
        var mapper = application.getBean(ObjectMapper.class);
        var created =
                mapper.readTree(post(application, PLAYERS.get(0), "/api/v1/lobbies", "{\"playerName\":\"Alice\"}"));
        var lobbyId = created.get("lobbyId").asString();
        post(application, PLAYERS.get(1), "/api/v1/lobbies/" + lobbyId + "/join", "{\"playerName\":\"Bob\"}");
        post(application, PLAYERS.get(2), "/api/v1/lobbies/" + lobbyId + "/join", "{\"playerName\":\"Cleo\"}");
        var started = mapper.readTree(post(application, PLAYERS.get(0), "/api/v1/lobbies/" + lobbyId + "/start", ""));
        return UUID.fromString(started.get("gameId").asString());
    }

    /** The status of reading a lobby that does not exist; every such read counts against the player's limit. */
    private int lobbyStatus(ConfigurableApplicationContext application, UUID player) throws Exception {
        return http.send(
                        HttpRequest.newBuilder(
                                        URI.create(baseUrl(application) + "/api/v1/lobbies/" + UUID.randomUUID()))
                                .header("Authorization", "Bearer " + player)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private String post(ConfigurableApplicationContext application, UUID player, String path, String body)
            throws Exception {
        var response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl(application) + path))
                        .header("Authorization", "Bearer " + player)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("POST %s -> %s", path, response.body())
                .isBetween(200, 299);
        return response.body();
    }

    private static List<String> eventTypes(List<Map<String, Object>> sink, UUID gameId) {
        return sink.stream()
                .filter(message -> gameId.toString().equals(message.get("key")))
                .map(message ->
                        ((Map<?, ?>) message.get("headers")).get("eventType").toString())
                .toList();
    }

    private static Map<String, Object> eraResolutionCompleted(
            ConfigurableApplicationContext application, UUID gameId, UUID eventId) {
        var mapper = application.getBean(ObjectMapper.class);
        var headers = new LinkedHashMap<String, String>();
        headers.put("eventType", "EraResolutionCompleted");
        headers.put("eventId", eventId.toString());
        headers.put("aggregateId", gameId.toString());
        headers.put("aggregateType", "Timeline");
        headers.put("gameId", gameId.toString());
        headers.put("occurredAt", "2026-03-01T10:00:00Z");
        headers.put("version", "1");
        var message = new LinkedHashMap<String, Object>();
        message.put("topic", "timeline.events");
        message.put("key", gameId.toString());
        message.put("headers", headers);
        message.put("payload", mapper.writeValueAsBytes(new EraResolutionCompletedPayload(gameId, 1, List.of())));
        return message;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(Map<String, Object> message) {
        return (Map<String, String>) message.get("headers");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void deliver(Consumer delivery, Map<String, Object> message) {
        delivery.accept(message);
    }

    private static List<Long> timelinePositions(ConfigurableApplicationContext application) {
        return application.getBean(SourceWatermarks.class).current().stream()
                .filter(watermark -> watermark.topic().equals("timeline.events"))
                .map(SourceWatermark::nextOffset)
                .toList();
    }

    private static <T> T inTransaction(
            ConfigurableApplicationContext application, java.util.function.Supplier<T> work) {
        return application.getBean(TransactionTemplate.class).execute(status -> work.get());
    }

    private static ListAppender<ILoggingEvent> captureLogs(String loggerSuffix) {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var logger = (Logger) LoggerFactory.getLogger(
                "io.github.temporalrift.game.session.infrastructure.adapter.in.kafka." + loggerSuffix);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        return appender;
    }

    private static int port(ConfigurableApplicationContext application) {
        return ((WebServerApplicationContext) application).getWebServer().getPort();
    }

    private static String baseUrl(ConfigurableApplicationContext application) {
        return "http://localhost:" + port(application);
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
