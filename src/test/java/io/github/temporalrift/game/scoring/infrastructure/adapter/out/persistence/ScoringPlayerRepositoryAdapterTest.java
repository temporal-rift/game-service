package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoringPlayerRepositoryAdapterTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    ScoringPlayerJpaRepository jpaRepository;

    @InjectMocks
    ScoringPlayerRepositoryAdapter adapter;

    @Test
    void upsertPlayerName_nullGameId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.upsertPlayerName(null, PLAYER_ID, "Ada"));

        then(jpaRepository).should(never()).upsert(any(), any(), any(), any());
    }

    @Test
    void upsertPlayerName_nullPlayerId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.upsertPlayerName(GAME_ID, null, "Ada"));

        then(jpaRepository).should(never()).upsert(any(), any(), any(), any());
    }

    @Test
    void upsertPlayerName_nullPlayerName_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.upsertPlayerName(GAME_ID, PLAYER_ID, null));

        then(jpaRepository).should(never()).upsert(any(), any(), any(), any());
    }

    @Test
    void upsertPlayerName_upsertsWithGeneratedIdAndGivenFields() {
        adapter.upsertPlayerName(GAME_ID, PLAYER_ID, "Ada");

        var idCaptor = ArgumentCaptor.forClass(UUID.class);
        then(jpaRepository).should().upsert(idCaptor.capture(), eq(GAME_ID), eq(PLAYER_ID), eq("Ada"));
        assertThat(idCaptor.getValue()).isNotNull();
    }
}
