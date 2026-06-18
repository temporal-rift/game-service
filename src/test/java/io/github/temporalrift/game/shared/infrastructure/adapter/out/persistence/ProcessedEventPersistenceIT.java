package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessedEventPersistenceIT {

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void tryMarkProcessed_firstClaimWinsDuplicatesAreIgnoredPerConsumer() {
        var eventId = UUID.randomUUID();

        var firstClaim = claim(eventId, "consumer-a");
        var duplicateClaim = claim(eventId, "consumer-a");
        var otherConsumerClaim = claim(eventId, "consumer-b");

        assertThat(firstClaim).isTrue();
        assertThat(duplicateClaim).isFalse();
        assertThat(otherConsumerClaim).isTrue();
        assertThat(processedRows()).isEqualTo(2);
    }

    @Test
    void tryMarkProcessed_withoutSurroundingTransaction_failsFast() {
        assertThatThrownBy(() -> processedEventRepository.tryMarkProcessed(UUID.randomUUID(), "consumer-a"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private boolean claim(UUID eventId, String consumer) {
        return Boolean.TRUE.equals(
                transactionTemplate.execute(_ -> processedEventRepository.tryMarkProcessed(eventId, consumer)));
    }

    private Integer processedRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class);
    }
}
