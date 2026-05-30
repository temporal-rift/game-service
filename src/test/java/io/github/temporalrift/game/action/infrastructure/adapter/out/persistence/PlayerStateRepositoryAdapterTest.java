package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

@ExtendWith(MockitoExtension.class)
class PlayerStateRepositoryAdapterTest {

    @Mock
    PlayerStateJpaRepository jpaRepository;

    @InjectMocks
    PlayerStateRepositoryAdapter adapter;

    @Test
    void save_mapsDomainToEntity() {
        var state = PlayerState.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Faction.ACTIVISTS,
                List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM)),
                true);

        adapter.save(state);

        var captor = ArgumentCaptor.forClass(PlayerStateJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getFaction()).isEqualTo(Faction.ACTIVISTS.name());
        assertThat(entity.isJammed()).isTrue();
        assertThat(entity.getHand()).containsExactlyElementsOf(state.hand());
    }

    @Test
    void findByGameIdAndPlayerId_mapsEntityToDomain() {
        var entity = new PlayerStateJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(UUID.randomUUID());
        entity.setPlayerId(UUID.randomUUID());
        entity.setFaction(Faction.WEAVERS.name());
        entity.setJammed(false);
        entity.setHand(List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.SCAN)));
        given(jpaRepository.findByGameIdAndPlayerId(entity.getGameId(), entity.getPlayerId()))
                .willReturn(Optional.of(entity));

        var loaded = adapter.findByGameIdAndPlayerId(entity.getGameId(), entity.getPlayerId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().faction()).isEqualTo(Faction.WEAVERS);
        assertThat(loaded.get().hand()).containsExactlyElementsOf(entity.getHand());
    }
}
