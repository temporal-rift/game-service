package io.github.temporalrift.game.session.application.listener;

import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.SpecialAction;

@ExtendWith(MockitoExtension.class)
class SessionActivistDeclarationProjectionListenerTest {

    @Mock
    SessionActivistDeclarationRepository repository;

    @InjectMocks
    SessionActivistDeclarationProjectionListener listener;

    @Test
    void onActivistDeclarationRecorded_projectsOnlyTheCurrentEraPredicateFields() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        listener.onActivistDeclarationRecorded(new ActivistDeclarationRecorded(
                gameId, 2, 1, playerId, SpecialAction.RALLY, targetEventId, UUID.randomUUID()));

        then(repository).should().saveIfAbsent(gameId, 2, playerId, targetEventId, SpecialAction.RALLY);
    }
}
