package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JacksonAutoConfiguration.class, HandSelectionRepositoryAdapter.class
})
class HandSelectionRepositoryAdapterIT {

    @Autowired
    HandSelectionRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void createIfAbsent_duplicateDoesNotAbortTheTransactionOrScheduleAnotherSelection() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var selection = selection(gameId, playerId);
        var duplicate = selection(gameId, playerId);
        var anotherPlayer = selection(gameId, UUID.randomUUID());

        transactionTemplate.executeWithoutResult(_ -> {
            assertThat(repository.createIfAbsent(selection)).isTrue();
            assertThat(repository.createIfAbsent(duplicate)).isFalse();
            assertThat(repository.createIfAbsent(anotherPlayer)).isTrue();
        });

        Optional<HandSelection> loaded = transactionTemplate.execute(
                _ -> repository.findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 1, playerId));

        assertThat(loaded).contains(selection);
    }

    @Test
    void saveAndLoad_roundTripsNonDefaultGradesForDealtAndSelectedCards() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var openSelection = selection(gameId, playerId);
        var selection = openSelection.select(
                openSelection.dealtCards().stream()
                        .limit(5)
                        .map(HandDealt.CardInstance::cardInstanceId)
                        .collect(java.util.stream.Collectors.toSet()),
                Instant.EPOCH);

        transactionTemplate.executeWithoutResult(_ -> repository.save(selection));

        Optional<HandSelection> loaded = transactionTemplate.execute(
                _ -> repository.findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 1, playerId));

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().dealtCards())
                .extracting(HandDealt.CardInstance::grade)
                .containsOnly(CardGrade.II);
        assertThat(loaded.orElseThrow().selectedCards())
                .extracting(HandDealt.CardInstance::grade)
                .containsOnly(CardGrade.II);
    }

    private static HandSelection selection(UUID gameId, UUID playerId) {
        var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(slot -> new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.II, slot))
                .toList();
        return HandSelection.open(new HandDealt(gameId, 1, playerId, Instant.parse("2026-08-14T00:01:00Z"), cards));
    }
}
