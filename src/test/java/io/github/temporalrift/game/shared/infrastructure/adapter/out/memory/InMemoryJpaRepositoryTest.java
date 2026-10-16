package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

class InMemoryJpaRepositoryTest {

    static class Row {
        UUID id = UUID.randomUUID();
        String name;
        List<String> tags = new ArrayList<>();
        Set<Integer> numbers = new java.util.HashSet<>();
        Map<String, Instant> times = new HashMap<>();
        UUID[] ids = new UUID[0];
        Row self = this;

        Row() {}

        Row(String name) {
            this.name = name;
        }
    }

    record Pair(String left, List<String> right) {}

    static class Rows extends InMemoryJpaRepository<Row, UUID> {

        Rows(InMemoryDatabase database) {
            super(database, TableDefinition.of("rows", row -> row.id));
        }

        List<Row> named(String name) {
            return findMatching(row -> name.equals(row.name), Comparator.comparing(row -> row.id.toString()));
        }
    }

    private Rows rows;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var database = new InMemoryDatabase(Duration.ofSeconds(1));
        rows = new Rows(database);
        transactions = new TransactionTemplate(new InMemoryTransactionManager(database));
    }

    @Test
    void savedAndLoadedRowsAreIndependentCopies() {
        var row = new Row("a");
        row.tags.add("x");
        row.times.put("t", Instant.EPOCH);
        rows.save(row);
        row.tags.add("changed after save");

        var loaded = rows.findById(row.id).orElseThrow();
        loaded.tags.add("changed after load");

        var again = rows.findById(row.id).orElseThrow();
        assertThat(again.tags).containsExactly("x");
        assertThat(again.times).containsEntry("t", Instant.EPOCH);
        assertThat(again.self).isSameAs(again);
        assertThat(again).isNotSameAs(row);
    }

    @Test
    void aLoadedRowChangedWithoutSavingIsWrittenBackBeforeReadsAndAtCommit() {
        var row = rows.save(new Row("before"));

        transactions.executeWithoutResult(status -> {
            rows.findById(row.id).orElseThrow().name = "after";
            assertThat(rows.named("after")).hasSize(1);
        });

        assertThat(rows.findById(row.id).orElseThrow().name).isEqualTo("after");
    }

    @Test
    void aChangeToALoadedRowIsDiscardedByRollback() {
        var row = rows.save(new Row("before"));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    rows.findById(row.id).orElseThrow().name = "after";
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.findById(row.id).orElseThrow().name).isEqualTo("before");
    }

    @Test
    void aDeletedRowIsNotResurrectedByItsTrackedCopy() {
        var row = rows.save(new Row("before"));

        transactions.executeWithoutResult(status -> {
            rows.findById(row.id).orElseThrow().name = "changed";
            rows.deleteById(row.id);
        });

        assertThat(rows.findById(row.id)).isEmpty();
    }

    @Test
    void projectionsReadRowsWithoutCopyingThem() {
        rows.save(new Row("a"));
        rows.save(new Row("b"));

        assertThat(rows.project(row -> row.name.equals("a"), row -> row.name)).containsExactly("a");
    }

    @Test
    void crudOperationsBehaveLikeARepository() {
        var first = rows.save(new Row("a"));
        var second = new Row("b");
        rows.saveAll(List.of(second));
        rows.saveAndFlush(new Row("c"));
        rows.saveAllAndFlush(List.of(new Row("d")));
        rows.flush();

        assertThat(rows.count()).isEqualTo(4);
        assertThat(rows.existsById(first.id)).isTrue();
        assertThat(rows.findAll()).hasSize(4);
        assertThat(rows.findAllById(List.of(first.id, second.id, UUID.randomUUID())))
                .hasSize(2);
        assertThat(rows.getReferenceById(first.id).name).isEqualTo("a");
        assertThat(rows.getById(first.id).name).isEqualTo("a");
        assertThat(rows.getOne(first.id).name).isEqualTo("a");
        assertThatThrownBy(() -> rows.getReferenceById(UUID.randomUUID())).isInstanceOf(EntityNotFoundException.class);

        rows.deleteById(first.id);
        rows.delete(second);
        rows.deleteAllById(List.of(UUID.randomUUID()));
        assertThat(rows.count()).isEqualTo(2);
        rows.deleteAll(rows.findAll());
        assertThat(rows.count()).isZero();
        rows.save(new Row("e"));
        rows.deleteAllInBatch(rows.findAll());
        rows.save(new Row("f"));
        rows.deleteAllByIdInBatch(List.of(rows.findAll().getFirst().id));
        rows.save(new Row("g"));
        rows.deleteAllInBatch();
        rows.save(new Row("h"));
        rows.deleteAll();
        assertThat(rows.findAll()).isEmpty();
        assertThat(rows.named("a")).isEmpty();
    }

    @Test
    void unsupportedQueriesSayTheyAreUnsupported() {
        assertThatThrownBy(() -> rows.findAll(Sort.unsorted())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.findAll(Pageable.unpaged())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.findOne(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.count(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.exists(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesHandleArraysRecordsAndNestedCollections() {
        var pair = new Pair("l", new ArrayList<>(List.of("r")));
        var copy = RowCopier.copy(pair);
        copy.right().add("changed");
        assertThat(pair.right()).containsExactly("r");

        var row = new Row("z");
        row.ids = new UUID[] {UUID.randomUUID()};
        row.numbers.add(3);
        var copied = RowCopier.copy(row);
        assertThat(copied.ids).containsExactly(row.ids);
        assertThat(copied.ids).isNotSameAs(row.ids);
        assertThat(copied.numbers).containsExactly(3);
        Object nothing = RowCopier.copy(null);
        assertThat(nothing).isNull();
        assertThat(RowCopier.copy("text")).isEqualTo("text");
    }

    @Test
    void postgresOrdersUuidsAsUnsignedBytes() {
        var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        assertThat(UuidOrder.POSTGRES.compare(low, high)).isNegative();
        assertThat(low.compareTo(high)).isPositive();
    }

    @Test
    void compositeUniqueKeysIgnoreNullParts() {
        assertThat(TableDefinition.key(1, "a")).isEqualTo(List.of(1, "a"));
        assertThat(TableDefinition.key(1, null)).isNull();
    }
}
