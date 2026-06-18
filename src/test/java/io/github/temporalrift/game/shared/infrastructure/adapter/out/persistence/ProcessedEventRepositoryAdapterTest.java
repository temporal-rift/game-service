package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessedEventRepositoryAdapterTest {

    @Mock
    ProcessedEventJpaRepository jpaRepository;

    @InjectMocks
    ProcessedEventRepositoryAdapter adapter;

    @Test
    @DisplayName("tryMarkProcessed — true when insert creates a new processed_events row")
    void tryMarkProcessed_inserted_returnsTrue() {
        // given
        var eventId = UUID.randomUUID();
        given(jpaRepository.insertIfAbsent(eventId, "consumer")).willReturn(1);

        // when / then
        assertThat(adapter.tryMarkProcessed(eventId, "consumer")).isTrue();
    }

    @Test
    @DisplayName("tryMarkProcessed — false when unique key reports duplicate")
    void tryMarkProcessed_duplicate_returnsFalse() {
        // given
        var eventId = UUID.randomUUID();
        given(jpaRepository.insertIfAbsent(eventId, "consumer")).willReturn(0);

        // when / then
        assertThat(adapter.tryMarkProcessed(eventId, "consumer")).isFalse();
    }
}
