package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;

/**
 * A Spring Data repository over an {@link InMemoryTable}. Entities are copied on the way in and out, so a caller
 * changing what it read changes nothing until it saves, as with a database. Query-by-example, sorting and paging are
 * not supported; a repository implements the queries its interface declares through the protected helpers.
 */
public abstract class InMemoryJpaRepository<E, ID> implements JpaRepository<E, ID> {

    private final InMemoryDatabase database;
    private final InMemoryTable<ID, E> table;
    private final Function<E, ID> idOf;

    protected InMemoryJpaRepository(InMemoryDatabase database, TableDefinition<ID, E> definition) {
        this.database = database;
        this.table = database.table(definition);
        this.idOf = definition.primaryKey();
    }

    protected final Optional<E> findFirst(Predicate<? super E> filter) {
        return table.findAll(filter).stream().findFirst().map(RowCopier::copy);
    }

    protected final List<E> findMatching(Predicate<? super E> filter) {
        return table.findAll(filter).stream().map(RowCopier::copy).toList();
    }

    protected final List<E> findMatching(Predicate<? super E> filter, Comparator<? super E> order) {
        return table.findAll(filter).stream().sorted(order).map(RowCopier::copy).toList();
    }

    protected final Optional<E> findByIdForUpdate(ID id) {
        return table.findForUpdate(id).map(RowCopier::copy);
    }

    protected final Optional<E> findFirstForUpdate(Predicate<? super E> filter) {
        return table.findAllForUpdate(filter, (left, right) -> 0).stream()
                .findFirst()
                .map(RowCopier::copy);
    }

    protected final List<E> findMatchingForUpdate(Predicate<? super E> filter, Comparator<? super E> order) {
        return table.findAllForUpdate(filter, order).stream()
                .map(RowCopier::copy)
                .toList();
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
    public Optional<E> findById(ID id) {
        return table.find(id).map(RowCopier::copy);
    }

    @Override
    public boolean existsById(ID id) {
        return table.find(id).isPresent();
    }

    @Override
    public List<E> findAll() {
        return findMatching(row -> true);
    }

    @Override
    public List<E> findAllById(Iterable<ID> ids) {
        var found = new ArrayList<E>();
        ids.forEach(id -> findById(id).ifPresent(found::add));
        return found;
    }

    @Override
    public long count() {
        return table.findAll(row -> true).size();
    }

    @Override
    public void deleteById(ID id) {
        table.delete(id);
    }

    @Override
    public void delete(E entity) {
        table.delete(idOf.apply(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
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
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        deleteAllById(ids);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public E getOne(ID id) {
        return getReferenceById(id);
    }

    @Override
    public E getById(ID id) {
        return getReferenceById(id);
    }

    @Override
    public E getReferenceById(ID id) {
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
        throw unsupported("query by example");
    }

    @Override
    public <S extends E> List<S> findAll(Example<S> example) {
        throw unsupported("query by example");
    }

    @Override
    public <S extends E> List<S> findAll(Example<S> example, Sort sort) {
        throw unsupported("query by example");
    }

    @Override
    public <S extends E> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw unsupported("query by example");
    }

    @Override
    public <S extends E> long count(Example<S> example) {
        throw unsupported("query by example");
    }

    @Override
    public <S extends E> boolean exists(Example<S> example) {
        throw unsupported("query by example");
    }

    @Override
    public <S extends E, R> R findBy(
            Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw unsupported("query by example");
    }

    private static UnsupportedOperationException unsupported(String feature) {
        return new UnsupportedOperationException("In-memory repositories do not support " + feature);
    }
}
