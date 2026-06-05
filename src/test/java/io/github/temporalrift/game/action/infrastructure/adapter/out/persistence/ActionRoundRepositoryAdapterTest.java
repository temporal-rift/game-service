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
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;

@ExtendWith(MockitoExtension.class)
class ActionRoundRepositoryAdapterTest {

    @Mock
    ActionRoundJpaRepository jpaRepository;

    @InjectMocks
    ActionRoundRepositoryAdapter adapter;

    @Test
    void save_mapsDomainToEntity() {
        var playerId = UUID.randomUUID();
        var round = new ActionRound(UUID.randomUUID(), UUID.randomUUID(), 1, 2, List.of(playerId), 45);
        round.pullEvents();
        var cardId = UUID.randomUUID();
        round.submitCard(playerId, cardId, CardType.PUSH, UUID.randomUUID(), UUID.randomUUID(), List.of(cardId));
        round.close("TIMER_EXPIRED");

        adapter.save(round);

        var captor = ArgumentCaptor.forClass(ActionRoundJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(round.id());
        assertThat(entity.getGameId()).isEqualTo(round.gameId());
        assertThat(entity.getStatus()).isEqualTo(RoundStatus.CLOSED.name());
        assertThat(entity.getTimerSeconds()).isEqualTo(45);
        assertThat(entity.getClosedReason()).isEqualTo("TIMER_EXPIRED");
        assertThat(entity.getPendingPlayerIds()).containsExactly(playerId);
        assertThat(entity.getSubmittedActions()).hasSize(1);
    }

    @Test
    void findById_mapsEntityToDomain() {
        var entity = new ActionRoundJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(UUID.randomUUID());
        entity.setEraNumber(1);
        entity.setRoundNumber(3);
        entity.setStatus(RoundStatus.CLOSED.name());
        entity.setTimerSeconds(30);
        entity.setClosedReason("ALL_SUBMITTED");
        entity.setPendingPlayerIds(new UUID[0]);
        entity.setSubmittedActions(List.of(new SubmittedAction.SpecialActionSubmission(
                UUID.randomUUID(),
                Faction.PROPHETS,
                SpecialAction.SEAL,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID())));
        given(jpaRepository.findById(entity.getId())).willReturn(Optional.of(entity));

        var loaded = adapter.findById(entity.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().status()).isEqualTo(RoundStatus.CLOSED);
        assertThat(loaded.get().timerSeconds()).isEqualTo(30);
        assertThat(loaded.get().closedReason()).isEqualTo("ALL_SUBMITTED");
        assertThat(loaded.get().submittedActions())
                .singleElement()
                .isInstanceOf(SubmittedAction.SpecialActionSubmission.class);
    }
}
