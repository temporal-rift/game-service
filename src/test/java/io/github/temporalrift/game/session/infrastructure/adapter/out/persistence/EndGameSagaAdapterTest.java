package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaStatus;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@ExtendWith(MockitoExtension.class)
class EndGameSagaAdapterTest {

    @Mock
    EndGameSagaStateJpaRepository jpaRepository;

    @InjectMocks
    EndGameSagaAdapter adapter;

    static EndGameSagaState sagaState() {
        return new EndGameSagaState(
                UUID.randomUUID(),
                EndGameTrigger.WIN_CONDITION_MET,
                EndGameSagaStatus.RUNNING,
                List.of(UUID.randomUUID()));
    }

    static EndGameSagaStateJpaEntity entityFor(EndGameSagaState state) {
        var entity = new EndGameSagaStateJpaEntity();
        entity.setGameId(state.gameId());
        entity.setTriggerType(state.triggerType().name());
        entity.setStatus(state.status().name());
        entity.setPlayerIds(state.playerIds());
        return entity;
    }

    @Test
    @DisplayName("save persists entity and returns the original saga state")
    void save_persistsAndReturnsSaga() {
        // given
        var state = sagaState();

        // when
        var result = adapter.save(state);

        // then
        assertThat(result).isEqualTo(state);
        then(jpaRepository).should().save(any(EndGameSagaStateJpaEntity.class));
    }

    @Test
    @DisplayName("claimIfAbsent inserts and returns true when no row exists yet")
    void claimIfAbsent_absent_returnsTrue() {
        // given
        var state = sagaState();
        given(jpaRepository.claimIfAbsent(
                        eq(state.gameId()),
                        eq(state.triggerType().name()),
                        eq(state.status().name()),
                        any()))
                .willReturn(1);

        // when
        var result = adapter.claimIfAbsent(state);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("claimIfAbsent returns false when a row for this gameId already exists")
    void claimIfAbsent_alreadyExists_returnsFalse() {
        // given
        var state = sagaState();
        given(jpaRepository.claimIfAbsent(
                        eq(state.gameId()),
                        eq(state.triggerType().name()),
                        eq(state.status().name()),
                        any()))
                .willReturn(0);

        // when
        var result = adapter.claimIfAbsent(state);

        // then
        assertThat(result).isFalse();
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
        assertThat(result.get().gameId()).isEqualTo(state.gameId());
        assertThat(result.get().triggerType()).isEqualTo(EndGameTrigger.WIN_CONDITION_MET);
        assertThat(result.get().status()).isEqualTo(EndGameSagaStatus.RUNNING);
        assertThat(result.get().playerIds()).isEqualTo(state.playerIds());
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
