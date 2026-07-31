package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;

@ExtendWith(MockitoExtension.class)
class ActivistDeclarationResolutionListenerTest {

    @Mock
    ActivistEraStateRepository activistEraStateRepository;

    @Test
    void resolution_updatesAndSavesTheRecordedActivistState() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var state = new ActivistEraState(UUID.randomUUID(), gameId, 2, playerId, false);
        state.declare(ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID());
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(gameId, 2, playerId))
                .willReturn(Optional.of(state));
        var listener = new ActivistDeclarationResolutionListener(activistEraStateRepository);

        listener.onActivistDeclarationResolved(new ActivistDeclarationResolved(gameId, 2, playerId, true));

        var saved = ArgumentCaptor.forClass(ActivistEraState.class);
        then(activistEraStateRepository).should().save(saved.capture());
        assertThat(saved.getValue().declarationSucceeded()).isTrue();
    }

    @Test
    void resolution_withoutAState_isIgnored() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(gameId, 2, playerId))
                .willReturn(Optional.empty());
        var listener = new ActivistDeclarationResolutionListener(activistEraStateRepository);

        listener.onActivistDeclarationResolved(new ActivistDeclarationResolved(gameId, 2, playerId, false));

        then(activistEraStateRepository).should(never()).save(any());
    }
}
