package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@ExtendWith(MockitoExtension.class)
class ActionRoundSagaAdapterTest {

    static final UUID SAGA_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final Instant EXPIRES_AT = Instant.parse("2099-01-01T00:00:30Z");
    static final List<UUID> PENDING_PLAYER_IDS = List.of(UUID.randomUUID(), UUID.randomUUID());

    @Mock
    ActionRoundSagaStateJpaRepository jpaRepository;

    @InjectMocks
    ActionRoundSagaAdapter adapter;

    @Test
    @DisplayName("save maps domain state to JPA entity and returns the domain object")
    void save_mapsToEntityAndReturnsState() {
        // given
        var state = new ActionRoundSagaState(
                SAGA_ID, GAME_ID, 1, 2, ActionRoundSagaStatus.WAITING, List.of(UUID.randomUUID()), EXPIRES_AT);

        // when
        var result = adapter.save(state);

        // then
        var captor = ArgumentCaptor.forClass(ActionRoundSagaStateJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getSagaId()).isEqualTo(SAGA_ID);
        assertThat(saved.getGameId()).isEqualTo(GAME_ID);
        assertThat(saved.getStatus()).isEqualTo(ActionRoundSagaStatus.WAITING.name());
        assertThat(saved.getPendingPlayerIds()).containsExactlyElementsOf(state.pendingPlayerIds());
        assertThat(result).isEqualTo(state);
    }

    @Test
    @DisplayName("find methods map JPA entities back to domain states")
    void findMethods_mapEntitiesBackToDomain() {
        // given
        var entity = entity(ActionRoundSagaStatus.CLOSING);
        given(jpaRepository.findById(SAGA_ID)).willReturn(Optional.of(entity));
        given(jpaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, 1, 2))
                .willReturn(Optional.of(entity));
        given(jpaRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 1, 2))
                .willReturn(Optional.of(entity));

        // when
        var byId = adapter.findBySagaId(SAGA_ID);
        var byRound = adapter.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, 1, 2);
        var byRoundWithLock = adapter.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 1, 2);

        // then
        assertThat(byId)
                .contains(new ActionRoundSagaState(
                        SAGA_ID, GAME_ID, 1, 2, ActionRoundSagaStatus.CLOSING, PENDING_PLAYER_IDS, EXPIRES_AT));
        assertThat(byRound).isPresent();
        assertThat(byRoundWithLock).isPresent();
        assertThat(byRound.get().status()).isEqualTo(ActionRoundSagaStatus.CLOSING);
    }

    @Test
    @DisplayName("findWaitingDueBy maps the bounded repository page to a domain list")
    void findWaitingDueBy_mapsRepositoryList() {
        // given
        var waitingEntity = entity(ActionRoundSagaStatus.WAITING);
        given(jpaRepository.findWaitingDueBy(eq(EXPIRES_AT), any(Pageable.class)))
                .willReturn(List.of(waitingEntity));

        // when
        var waiting = adapter.findWaitingDueBy(EXPIRES_AT);

        // then
        assertThat(waiting)
                .singleElement()
                .satisfies(state -> assertThat(state.status()).isEqualTo(ActionRoundSagaStatus.WAITING));
    }

    private ActionRoundSagaStateJpaEntity entity(ActionRoundSagaStatus status) {
        var entity = new ActionRoundSagaStateJpaEntity();
        entity.setSagaId(SAGA_ID);
        entity.setGameId(GAME_ID);
        entity.setEraNumber(1);
        entity.setRoundNumber(2);
        entity.setStatus(status.name());
        entity.setPendingPlayerIds(PENDING_PLAYER_IDS.toArray(UUID[]::new));
        entity.setTimerExpiresAt(EXPIRES_AT);
        return entity;
    }
}
