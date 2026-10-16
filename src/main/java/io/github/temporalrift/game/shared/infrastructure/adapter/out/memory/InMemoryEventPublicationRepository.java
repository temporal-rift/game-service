package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Spring Modulith publication registry's storage, kept in {@link InMemoryDatabase} so that a publication is
 * registered by the transaction that publishes it and discarded when that transaction rolls back. Events are held in
 * their serialized form, as the database registry holds them, and completed publications are updated in place.
 */
@Transactional
class InMemoryEventPublicationRepository implements EventPublicationRepository {

    static final TableDefinition<UUID, PublicationRow> PUBLICATIONS_TABLE =
            TableDefinition.of("event_publication", PublicationRow::id);

    private static final Comparator<PublicationRow> OLDEST_FIRST =
            Comparator.comparing(PublicationRow::publicationDate);

    private final InMemoryTable<UUID, PublicationRow> table;
    private final EventSerializer serializer;

    InMemoryEventPublicationRepository(InMemoryDatabase database, EventSerializer serializer) {
        this.table = database.table(PUBLICATIONS_TABLE);
        this.serializer = serializer;
    }

    @Override
    public TargetEventPublication create(TargetEventPublication publication) {
        var event = publication.getEvent();
        table.save(new PublicationRow(
                publication.getIdentifier(),
                publication.getPublicationDate(),
                publication.getTargetIdentifier().getValue(),
                serializer.serialize(event).toString(),
                event.getClass(),
                publication.getStatus(),
                null,
                publication.getLastResubmissionDate(),
                publication.getCompletionAttempts()));
        return publication;
    }

    @Override
    public void markProcessing(UUID identifier) {
        update(identifier, row -> row.status() != Status.PROCESSING, row -> row.withStatus(Status.PROCESSING));
    }

    @Override
    public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {
        var serialized = serializer.serialize(event).toString();
        table.findAll(row -> row.completionDate() == null
                        && row.listenerId().equals(identifier.getValue())
                        && row.serializedEvent().equals(serialized))
                .forEach(row -> table.save(row.completed(completionDate)));
    }

    @Override
    public void markCompleted(UUID identifier, Instant completionDate) {
        update(identifier, row -> true, row -> row.completed(completionDate));
    }

    @Override
    public void markFailed(UUID identifier) {
        update(identifier, row -> row.status() != Status.FAILED, row -> row.withStatus(Status.FAILED));
    }

    @Override
    public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {
        return update(identifier, row -> row.status() != Status.RESUBMITTED, row -> row.resubmitted(resubmissionDate));
    }

    @Override
    public List<TargetEventPublication> findIncompletePublications() {
        return find(row -> row.completionDate() == null);
    }

    @Override
    public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
        return find(row -> row.completionDate() == null && row.publicationDate().isBefore(instant));
    }

    @Override
    public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
            Object event, PublicationTargetIdentifier targetIdentifier) {
        var serialized = serializer.serialize(event).toString();
        return find(row -> row.completionDate() == null
                        && row.listenerId().equals(targetIdentifier.getValue())
                        && row.serializedEvent().equals(serialized))
                .stream()
                .findFirst();
    }

    @Override
    public List<TargetEventPublication> findCompletedPublications() {
        return find(row -> row.completionDate() != null && row.status() == Status.COMPLETED);
    }

    @Override
    public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {
        var reference = criteria.getPublicationDateReference();
        var failed = find(row -> row.status() == Status.FAILED
                && (reference == null || row.publicationDate().isBefore(reference)));
        var limit = criteria.getMaxItemsToRead();
        return limit == -1 ? failed : failed.stream().limit(limit).toList();
    }

    @Override
    public void deletePublications(List<UUID> identifiers) {
        identifiers.forEach(table::delete);
    }

    @Override
    public void deleteCompletedPublications() {
        table.deleteAll(row -> row.completionDate() != null);
    }

    @Override
    public void deleteCompletedPublicationsBefore(Instant instant) {
        table.deleteAll(
                row -> row.completionDate() != null && row.completionDate().isBefore(instant));
    }

    @Override
    public List<TargetEventPublication> findByStatus(Status status) {
        return find(row -> row.status() == status);
    }

    @Override
    public int countByStatus(Status status) {
        return table.findAll(row -> row.status() == status).size();
    }

    private boolean update(UUID identifier, Predicate<PublicationRow> condition, UnaryOperator<PublicationRow> change) {
        return table.findForUpdate(identifier)
                .filter(condition)
                .map(row -> {
                    table.save(change.apply(row));
                    return true;
                })
                .orElse(false);
    }

    private List<TargetEventPublication> find(Predicate<PublicationRow> filter) {
        return table.findAll(filter).stream()
                .sorted(OLDEST_FIRST)
                .<TargetEventPublication>map(row -> new StoredPublication(row, serializer))
                .toList();
    }

    record PublicationRow(
            UUID id,
            Instant publicationDate,
            String listenerId,
            String serializedEvent,
            Class<?> eventType,
            Status status,
            Instant completionDate,
            Instant lastResubmissionDate,
            int completionAttempts) {

        PublicationRow withStatus(Status newStatus) {
            return new PublicationRow(
                    id,
                    publicationDate,
                    listenerId,
                    serializedEvent,
                    eventType,
                    newStatus,
                    completionDate,
                    lastResubmissionDate,
                    completionAttempts);
        }

        PublicationRow completed(Instant at) {
            return new PublicationRow(
                    id,
                    publicationDate,
                    listenerId,
                    serializedEvent,
                    eventType,
                    Status.COMPLETED,
                    at,
                    lastResubmissionDate,
                    completionAttempts);
        }

        PublicationRow resubmitted(Instant at) {
            return new PublicationRow(
                    id,
                    publicationDate,
                    listenerId,
                    serializedEvent,
                    eventType,
                    Status.RESUBMITTED,
                    completionDate,
                    at,
                    completionAttempts + 1);
        }
    }

    private static final class StoredPublication implements TargetEventPublication {

        private final PublicationRow row;
        private final EventSerializer serializer;
        private Instant completionDate;
        private Object event;

        private StoredPublication(PublicationRow row, EventSerializer serializer) {
            this.row = row;
            this.serializer = serializer;
            this.completionDate = row.completionDate();
        }

        @Override
        public UUID getIdentifier() {
            return row.id();
        }

        @Override
        public Object getEvent() {
            if (event == null) {
                event = serializer.deserialize(row.serializedEvent(), row.eventType());
            }
            return event;
        }

        @Override
        public PublicationTargetIdentifier getTargetIdentifier() {
            return PublicationTargetIdentifier.of(row.listenerId());
        }

        @Override
        public Instant getPublicationDate() {
            return row.publicationDate();
        }

        @Override
        public Optional<Instant> getCompletionDate() {
            return Optional.ofNullable(completionDate);
        }

        @Override
        public void markCompleted(Instant instant) {
            this.completionDate = instant;
        }

        @Override
        public Status getStatus() {
            return completionDate != null && row.status() != Status.COMPLETED ? Status.COMPLETED : row.status();
        }

        @Override
        public int getCompletionAttempts() {
            return row.completionAttempts();
        }

        @Override
        public Instant getLastResubmissionDate() {
            return row.lastResubmissionDate();
        }
    }
}
