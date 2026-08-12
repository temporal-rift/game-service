package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ScoresUpdated;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=localhost:9092")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostgresTestcontainersConfiguration.class,
    EraSagaAdapter.class,
    EraSagaScoresUpdatedInboxRepositoryAdapter.class,
    EraSagaScoresUpdatedInboxRepositoryAdapterIT.JacksonTestConfiguration.class
})
class EraSagaScoresUpdatedInboxRepositoryAdapterIT {

    @Autowired
    EraSagaRepository eraSagaRepository;

    @Autowired
    EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;

    @Test
    void findRecordedButNotAdvanced_returnsEraWhoseSagaIsStillWaitingScores() {
        var gameId = UUID.randomUUID();
        eraSagaRepository.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
        var su = scoresUpdated(gameId, 1);

        scoresUpdatedInbox.record(su);

        assertThat(scoresUpdatedInbox.findRecordedButNotAdvanced()).containsExactly(su);
    }

    @Test
    void findRecordedButNotAdvanced_sagaAlreadyAdvanced_returnsNothing() {
        var gameId = UUID.randomUUID();
        eraSagaRepository.save(new EraSagaState(gameId, 1, EraSagaStatus.COMPLETED, List.of(UUID.randomUUID())));
        scoresUpdatedInbox.record(scoresUpdated(gameId, 1));

        assertThat(scoresUpdatedInbox.findRecordedButNotAdvanced()).isEmpty();
    }

    @Test
    void findRecordedButNotAdvanced_noInboxRecordYet_returnsNothing() {
        var gameId = UUID.randomUUID();
        eraSagaRepository.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));

        assertThat(scoresUpdatedInbox.findRecordedButNotAdvanced()).isEmpty();
    }

    @Test
    void findRecordedButNotAdvanced_staleInboxRowForEarlierEra_doesNotMatchLaterEra() {
        // saga has already moved on to era 2; a leftover era-1 inbox row must not be mistaken for
        // pending era-2 work
        var gameId = UUID.randomUUID();
        eraSagaRepository.save(new EraSagaState(gameId, 2, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
        scoresUpdatedInbox.record(scoresUpdated(gameId, 1));

        assertThat(scoresUpdatedInbox.findRecordedButNotAdvanced()).isEmpty();
    }

    @Test
    void record_duplicateDelivery_isIdempotent() {
        var gameId = UUID.randomUUID();
        eraSagaRepository.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
        var su = scoresUpdated(gameId, 1);

        scoresUpdatedInbox.record(su);
        scoresUpdatedInbox.record(su);

        assertThat(scoresUpdatedInbox.findRecordedButNotAdvanced()).hasSize(1);
    }

    private ScoresUpdated scoresUpdated(UUID gameId, int eraNumber) {
        return new ScoresUpdated(
                gameId,
                eraNumber,
                List.of(new ScoresUpdated.ScoreUpdate(UUID.randomUUID(), Faction.PROPHETS, 2, "bonus", 10)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JacksonTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
