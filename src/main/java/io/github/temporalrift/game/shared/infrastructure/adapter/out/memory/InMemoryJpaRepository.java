package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;
import tools.jackson.core.JacksonException;

/**
 * A Spring Data repository over an {@link InMemoryTable}. Entities are copied on the way in and out, so a caller
 * changing what it read changes nothing until it saves, as with a database. Query-by-example, sorting and paging are
 * not supported; a repository implements the queries its interface declares through the protected helpers.
 */
public abstract class InMemoryJpaRepository<E, I> implements JpaRepository<E, I> {

    private static final String QUERY_BY_EXAMPLE = "query by example";

    private final InMemoryDatabase database;
    private final InMemoryTable<I, E> table;
    private final Function<E, I> idOf;

    protected InMemoryJpaRepository(InMemoryDatabase database, TableDefinition<I, E> definition) {
        this.database = database;
        this.table = database.table(definition);
        this.idOf = definition.primaryKey();
    }

    protected final Optional<E> findFirst(Predicate<? super E> filter) {
        return table.findAll(filter).stream().findFirst().map(this::load);
    }

    protected final List<E> findMatching(Predicate<? super E> filter) {
        return table.findAll(filter).stream().map(this::load).toList();
    }

    protected final List<E> findMatching(Predicate<? super E> filter, Comparator<? super E> order) {
        return table.findAll(filter).stream().sorted(order).map(this::load).toList();
    }

    protected final Optional<E> findByIdForUpdate(I id) {
        return table.findForUpdate(id).map(this::load);
    }

    protected final Optional<E> findFirstForUpdate(Predicate<? super E> filter) {
        return table.findAllForUpdate(filter, (left, right) -> 0).stream()
                .findFirst()
                .map(this::load);
    }

    protected final List<E> findMatchingForUpdate(Predicate<? super E> filter, Comparator<? super E> order) {
        return table.findAllForUpdate(filter, order).stream().map(this::load).toList();
    }

    /**
     * Copies a stored row for the caller and tracks the copy in the active transaction, so a change the caller makes
     * without saving is written back before the next read and at commit, as a persistence context would.
     */
    private E load(E stored) {
        var copy = RowCopier.copy(stored);
        var transaction = database.current();
        if (transaction != null) {
            var snapshot = new Snapshot<E>(RowCopier.copy(copy));
            transaction.manage(table, idOf.apply(copy), () -> {
                if (!RowCopier.sameState(snapshot.state, copy)) {
                    table.save(RowCopier.copy(copy));
                    snapshot.state = RowCopier.copy(copy);
                }
            });
        }
        return copy;
    }

    private static final class Snapshot<T> {

        private T state;

        private Snapshot(T state) {
            this.state = state;
        }
    }

    /**
     * Maps the matching rows without copying them, for read-only sweeps; the rows are never handed out, so the mapper
     * must return values that do not alias them.
     */
    public final <T> List<T> project(Predicate<? super E> filter, Function<? super E, T> mapper) {
        return table.findAll(filter).stream().<T>map(mapper).toList();
    }

    /** Stores the entity unless its primary key or a unique value is taken, reporting whether it was stored. */
    protected final boolean insertRowIfAbsent(E entity) {
        return table.insertIfAbsent(RowCopier.copy(entity));
    }

    /**
     * Serializes concurrent upserts of one natural key until the transaction ends, so the second sees the first's row
     * and updates it instead of conflicting, as {@code ON CONFLICT DO UPDATE} does.
     */
    protected final void lockForUpsert(Object naturalKey) {
        database.lockUntilTransactionEnds(naturalKey);
    }

    /** Parses a JSON column value as the database would on insert: malformed text is rejected as bad data. */
    protected static <T> T parseJson(String column, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (JacksonException e) {
            throw new DataIntegrityViolationException("Malformed JSON for column " + column, e);
        }
    }

    protected final int deleteMatching(Predicate<? super E> filter) {
        return table.deleteAll(filter);
    }

    @Override
    public <S extends E> S save(S entity) {
        table.save(RowCopier.copy(entity));
        return entity;
    }

    @Override
    public <S extends E> List<S> saveAll(Iterable<S> entities) {
        var saved = new ArrayList<S>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    @Override
    public <S extends E> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends E> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public Optional<E> findById(I id) {
        return table.find(id).map(this::load);
    }

    @Override
    public boolean existsById(I id) {
        return table.find(id).isPresent();
    }

    @Override
    public List<E> findAll() {
        return findMatching(row -> true);
    }

    @Override
    public List<E> findAllById(Iterable<I> ids) {
        var found = new ArrayList<E>();
        ids.forEach(id -> findById(id).ifPresent(found::add));
        return found;
    }

    @Override
    public long count() {
        return table.findAll(row -> true).size();
    }

    @Override
    public void deleteById(I id) {
        table.delete(id);
    }

    @Override
    public void delete(E entity) {
        table.delete(idOf.apply(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends I> ids) {
        ids.forEach(table::delete);
    }

    @Override
    public void deleteAll(Iterable<? extends E> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        table.deleteAll(row -> true);
    }

    @Override
    public void flush() {
        // Writes are applied to the transaction as they are made.
    }

    @Override
    public void deleteAllInBatch(Iterable<E> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<I> ids) {
        deleteAllById(ids);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public E getOne(I id) {
        return getReferenceById(id);
    }

    @Override
    public E getById(I id) {
        return getReferenceById(id);
    }

    @Override
    public E getReferenceById(I id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No row " + id));
    }

    @Override
    public List<E> findAll(Sort sort) {
        throw unsupported("sorting");
    }

    @Override
    public Page<E> findAll(Pageable pageable) {
        throw unsupported("paging");
    }

    @Override
    public <S extends E> Optional<S> findOne(Example<S> example) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E> List<S> findAll(Example<S> example) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E> List<S> findAll(Example<S> example, Sort sort) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E> long count(Example<S> example) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E> boolean exists(Example<S> example) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    @Override
    public <S extends E, R> R findBy(
            Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw unsupported(QUERY_BY_EXAMPLE);
    }

    private static UnsupportedOperationException unsupported(String feature) {
        return new UnsupportedOperationException("In-memory repositories do not support " + feature);
    }
}
