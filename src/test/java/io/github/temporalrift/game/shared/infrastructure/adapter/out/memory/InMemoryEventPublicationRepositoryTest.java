package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class InMemoryEventPublicationRepositoryTest {

    record Announced(UUID id, String text) {}

    private static final PublicationTargetIdentifier LISTENER = PublicationTargetIdentifier.of("listener-a");
    private static final Instant PUBLISHED = Instant.parse("2026-03-01T10:00:00Z");

    private EventPublicationRepository publications;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var database = new InMemoryDatabase(Duration.ofSeconds(5));
        var mapper = JsonMapper.builder().build();
        var serializer = new EventSerializer() {
            @Override
            public Object serialize(Object event) {
                return mapper.writeValueAsString(event);
            }

            @Override
            public <T> T deserialize(Object serialized, Class<T> type) {
                return mapper.readValue(serialized.toString(), type);
            }
        };
        publications = new InMemoryEventPublicationRepository(database, serializer);
        transactions = new TransactionTemplate(new InMemoryTransactionManager(database));
    }

    @Test
    void aRegisteredPublicationIsIncompleteUntilCompleted() {
        var event = new Announced(UUID.randomUUID(), "hello");
        var publication = TargetEventPublication.of(event, LISTENER, PUBLISHED);

        transactions.executeWithoutResult(status -> publications.create(publication));

        assertThat(publications.findIncompletePublications())
                .singleElement()
                .satisfies(found -> assertThat(found.getEvent()).isEqualTo(event));

        transactions.executeWithoutResult(
                status -> publications.markCompleted(publication.getIdentifier(), PUBLISHED.plusSeconds(1)));

        assertThat(publications.findIncompletePublications()).isEmpty();
        assertThat(publications.findCompletedPublications())
                .singleElement()
                .satisfies(found -> assertThat(found.getStatus()).isEqualTo(Status.COMPLETED));
    }

    @Test
    void aRolledBackRegistrationLeavesNoPublication() {
        var publication = TargetEventPublication.of(new Announced(UUID.randomUUID(), "x"), LISTENER, PUBLISHED);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    publications.create(publication);
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(publications.findIncompletePublications()).isEmpty();
    }

    @Test
    void completionByEventAndListenerMatchesOnlyThatPair() {
        var event = new Announced(UUID.randomUUID(), "pair");
        var other = PublicationTargetIdentifier.of("listener-b");
        transactions.executeWithoutResult(status -> {
            publications.create(TargetEventPublication.of(event, LISTENER, PUBLISHED));
            publications.create(TargetEventPublication.of(event, other, PUBLISHED));
        });

        transactions.executeWithoutResult(status -> publications.markCompleted(event, LISTENER, PUBLISHED));

        assertThat(publications.findIncompletePublications())
                .singleElement()
                .satisfies(found -> assertThat(found.getTargetIdentifier()).isEqualTo(other));
        assertThat(publications.findIncompletePublicationsByEventAndTargetIdentifier(event, LISTENER))
                .isEmpty();
    }

    @Test
    void incompleteQueriesAreOrderedByAgeAndFilteredByCutoff() {
        var old = TargetEventPublication.of(new Announced(UUID.randomUUID(), "old"), LISTENER, PUBLISHED);
        var recent =
                TargetEventPublication.of(new Announced(UUID.randomUUID(), "new"), LISTENER, PUBLISHED.plusSeconds(60));
        transactions.executeWithoutResult(status -> {
            publications.create(recent);
            publications.create(old);
        });

        assertThat(publications.findIncompletePublications())
                .extracting(TargetEventPublication::getIdentifier)
                .containsExactly(old.getIdentifier(), recent.getIdentifier());
        assertThat(publications.findIncompletePublicationsPublishedBefore(PUBLISHED.plusSeconds(30)))
                .extracting(TargetEventPublication::getIdentifier)
                .containsExactly(old.getIdentifier());
    }

    @Test
    void failedPublicationsAreResubmittedOnceUntilTheyFailAgain() {
        var publication = TargetEventPublication.of(new Announced(UUID.randomUUID(), "retry"), LISTENER, PUBLISHED);
        transactions.executeWithoutResult(status -> {
            publications.create(publication);
            publications.markFailed(publication.getIdentifier());
        });

        assertThat(publications.countByStatus(Status.FAILED)).isEqualTo(1);
        Boolean resubmitted5 = transactions.execute(
                status -> publications.markResubmitted(publication.getIdentifier(), PUBLISHED.plusSeconds(5)));
        assertThat(resubmitted5).isTrue();
        Boolean resubmitted6 = transactions.execute(
                status -> publications.markResubmitted(publication.getIdentifier(), PUBLISHED.plusSeconds(6)));
        assertThat(resubmitted6).isFalse();
        assertThat(publications.findByStatus(Status.RESUBMITTED))
                .singleElement()
                .satisfies(found ->
                        assertThat(found.getCompletionAttempts()).isEqualTo(publication.getCompletionAttempts() + 1));
    }

    @Test
    void completedPublicationsCanBeDeletedByAge() {
        var publication = TargetEventPublication.of(new Announced(UUID.randomUUID(), "gone"), LISTENER, PUBLISHED);
        transactions.executeWithoutResult(status -> {
            publications.create(publication);
            publications.markCompleted(publication.getIdentifier(), PUBLISHED.plusSeconds(1));
        });

        transactions.executeWithoutResult(
                status -> publications.deleteCompletedPublicationsBefore(PUBLISHED.plusSeconds(2)));

        assertThat(publications.findCompletedPublications()).isEmpty();
    }
}
