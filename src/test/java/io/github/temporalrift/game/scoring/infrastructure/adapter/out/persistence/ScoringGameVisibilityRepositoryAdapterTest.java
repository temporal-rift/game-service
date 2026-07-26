package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoringGameVisibilityRepositoryAdapterTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    ScoringGameVisibilityJpaRepository jpaRepository;

    @Mock
    Clock clock;

    @InjectMocks
    ScoringGameVisibilityRepositoryAdapter adapter;

    @Test
    void areFactionsRevealed_nullGameId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.areFactionsRevealed(null));
    }

    @Test
    void areFactionsRevealed_returnsTrueWhenRowSaysRevealed() {
        var entity = new ScoringGameVisibilityJpaEntity();
        entity.setGameId(GAME_ID);
        entity.setFactionsRevealed(true);
        entity.setUpdatedAt(NOW);
        given(jpaRepository.findById(GAME_ID)).willReturn(Optional.of(entity));

        assertThat(adapter.areFactionsRevealed(GAME_ID)).isTrue();
    }

    @Test
    void areFactionsRevealed_returnsFalseWhenNoRowExists() {
        given(jpaRepository.findById(GAME_ID)).willReturn(Optional.empty());

        assertThat(adapter.areFactionsRevealed(GAME_ID)).isFalse();
    }

    @Test
    void markFactionsRevealed_nullGameId_throws() {
        assertThatNullPointerException().isThrownBy(() -> adapter.markFactionsRevealed(null));

        then(jpaRepository).should(never()).upsertRevealed(any(), any());
    }

    @Test
    void markFactionsRevealed_upsertsWithCurrentClockInstant() {
        given(clock.instant()).willReturn(NOW);

        adapter.markFactionsRevealed(GAME_ID);

        then(jpaRepository).should().upsertRevealed(GAME_ID, NOW);
    }
}
