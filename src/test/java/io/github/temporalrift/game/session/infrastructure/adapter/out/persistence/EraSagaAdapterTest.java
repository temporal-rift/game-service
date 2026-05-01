package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@ExtendWith(MockitoExtension.class)
class EraSagaAdapterTest {

    @Mock
    EraSagaStateJpaRepository jpaRepository;

    @InjectMocks
    EraSagaAdapter adapter;

    static EraSagaState sagaState() {
        return new EraSagaState(UUID.randomUUID(), 1, EraSagaStatus.WAITING_ROUND_1, List.of(UUID.randomUUID()));
    }

    static EraSagaStateJpaEntity entityFor(EraSagaState state) {
        var e = new EraSagaStateJpaEntity();
        e.setGameId(state.gameId());
        e.setEraNumber(state.eraNumber());
        e.setStatus(state.status().name());
        e.setPlayerIds(state.playerIds());
        return e;
    }

    @Test
    @DisplayName("save persists entity and returns the original saga state")
    void save_persistsAndReturnsSaga() {
        // given
        var state = sagaState();
        given(jpaRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        var result = adapter.save(state);

        // then
        assertThat(result).isEqualTo(state);
        then(jpaRepository).should().save(any(EraSagaStateJpaEntity.class));
    }

    @Test
    @DisplayName("findByGameId returns mapped domain object when entity exists")
    void findByGameId_found_returnsMappedState() {
        // given
        var state = sagaState();
        given(jpaRepository.findById(state.gameId())).willReturn(Optional.of(entityFor(state)));

        // when
        var result = adapter.findByGameId(state.gameId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameId()).isEqualTo(state.gameId());
        assertThat(result.get().status()).isEqualTo(EraSagaStatus.WAITING_ROUND_1);
        assertThat(result.get().playerIds()).isEqualTo(state.playerIds());
    }

    @Test
    @DisplayName("findByGameId returns empty when no entity exists")
    void findByGameId_notFound_returnsEmpty() {
        // given
        var gameId = UUID.randomUUID();
        given(jpaRepository.findById(gameId)).willReturn(Optional.empty());

        // when
        var result = adapter.findByGameId(gameId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGameIdWithLock returns mapped domain object when entity exists")
    void findByGameIdWithLock_found_returnsMappedState() {
        // given
        var state = sagaState();
        given(jpaRepository.findByGameIdWithLock(state.gameId())).willReturn(Optional.of(entityFor(state)));

        // when
        var result = adapter.findByGameIdWithLock(state.gameId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().eraNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByGameIdWithLock returns empty when no entity exists")
    void findByGameIdWithLock_notFound_returnsEmpty() {
        // given
        var gameId = UUID.randomUUID();
        given(jpaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.empty());

        // when
        var result = adapter.findByGameIdWithLock(gameId);

        // then
        assertThat(result).isEmpty();
    }
}
