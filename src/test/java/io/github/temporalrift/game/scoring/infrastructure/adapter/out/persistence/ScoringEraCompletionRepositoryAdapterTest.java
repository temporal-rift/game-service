package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.playerscore.InvalidScoreEraException;

@ExtendWith(MockitoExtension.class)
class ScoringEraCompletionRepositoryAdapterTest {

    @Mock
    ScoringEraCompletionJpaRepository jpaRepository;

    @InjectMocks
    ScoringEraCompletionRepositoryAdapter adapter;

    @Test
    void tryMarkScoringComplete_returnsTrueWhenClaimSucceeds() {
        var gameId = UUID.randomUUID();
        given(jpaRepository.insertIfAbsent(gameId, 1)).willReturn(1);

        assertThat(adapter.tryMarkScoringComplete(gameId, 1)).isTrue();
    }

    @Test
    void tryMarkScoringComplete_returnsFalseWhenAlreadyClaimed() {
        var gameId = UUID.randomUUID();
        given(jpaRepository.insertIfAbsent(gameId, 1)).willReturn(0);

        assertThat(adapter.tryMarkScoringComplete(gameId, 1)).isFalse();
    }

    @Test
    void tryMarkScoringComplete_rejectsNullGameId() {
        assertThatThrownBy(() -> adapter.tryMarkScoringComplete(null, 1)).isInstanceOf(NullPointerException.class);

        then(jpaRepository).should(never()).insertIfAbsent(any(), anyInt());
    }

    @Test
    void tryMarkScoringComplete_rejectsEraNumberBelowOne() {
        var gameId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.tryMarkScoringComplete(gameId, 0))
                .isInstanceOf(InvalidScoreEraException.class);

        then(jpaRepository).should(never()).insertIfAbsent(any(), anyInt());
    }
}
