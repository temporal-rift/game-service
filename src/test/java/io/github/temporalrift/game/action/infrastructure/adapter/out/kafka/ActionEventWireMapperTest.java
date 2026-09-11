package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.HandCardIntercepted;
import io.github.temporalrift.game.action.domain.event.InfluenceTraced;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerJammed;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

class ActionEventWireMapperTest {

    private final ActionEventWireMapper mapper = Mappers.getMapper(ActionEventWireMapper.class);

    @Test
    void exposeBehaviorChanged_mapsToTheContractPayloadShape() {
        var gameId = UUID.randomUUID();
        var activistPlayerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var domain = new ExposeBehaviorChanged(gameId, 2, 3, activistPlayerId, targetPlayerId);

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(gameId);
        assertThat(wire.eraNumber()).isEqualTo(2);
        assertThat(wire.roundNumber()).isEqualTo(3);
        assertThat(wire.activistPlayerId()).isEqualTo(activistPlayerId);
        assertThat(wire.targetPlayerId()).isEqualTo(targetPlayerId);
    }

    @Test
    void paradoxResolutionCardPlayed_mapsWithoutRoundCoordinates() {
        var domain = new ParadoxResolutionCardPlayed(
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CardType.DETONATE,
                UUID.randomUUID(),
                UUID.randomUUID());

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(domain.gameId());
        assertThat(wire.eraNumber()).isEqualTo(domain.eraNumber());
        assertThat(wire.playerId()).isEqualTo(domain.playerId());
        assertThat(wire.cardInstanceId()).isEqualTo(domain.cardInstanceId());
        assertThat(wire.cardType().name()).isEqualTo("DETONATE");
        assertThat(wire.targetEventId()).isEqualTo(domain.targetEventId());
        assertThat(wire.targetOutcomeId()).isEqualTo(domain.targetOutcomeId());
    }

    @Test
    void cardPlayedMapsPrivateScanTargetSelection() {
        var targets = List.of(UUID.randomUUID(), UUID.randomUUID());
        var domain = new CardPlayed(
                UUID.randomUUID(),
                2,
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CardType.SCAN,
                CardGrade.II,
                null,
                targets,
                null,
                null,
                null);

        var wire = mapper.toWire(domain);

        assertThat(wire.targetEventId()).isNull();
        assertThat(wire.targetEventIds()).containsExactlyElementsOf(targets);
        assertThat(wire.targetPlayerId()).isNull();
    }

    @Test
    void playerJammed_mapsOnlySuppressedViewerAndBoundedRound() {
        var domain = new PlayerJammed(UUID.randomUUID(), 2, UUID.randomUUID(), 3);

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(domain.gameId());
        assertThat(wire.eraNumber()).isEqualTo(domain.eraNumber());
        assertThat(wire.playerId()).isEqualTo(domain.playerId());
        assertThat(wire.jammedUntilRound()).isEqualTo(domain.jammedUntilRound());
    }

    @Test
    void handCardIntercepted_mapsViewerTargetAndRevealedCards() {
        var revealed = new HandCardIntercepted.RevealedCard(UUID.randomUUID(), CardType.SWING, CardGrade.III);
        var domain = new HandCardIntercepted(
                UUID.randomUUID(), 2, 1, UUID.randomUUID(), UUID.randomUUID(), List.of(revealed));

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(domain.gameId());
        assertThat(wire.eraNumber()).isEqualTo(domain.eraNumber());
        assertThat(wire.roundNumber()).isEqualTo(domain.roundNumber());
        assertThat(wire.playerId()).isEqualTo(domain.playerId());
        assertThat(wire.targetPlayerId()).isEqualTo(domain.targetPlayerId());
        assertThat(wire.revealedCards()).hasSize(1);
        assertThat(wire.revealedCards().getFirst().cardInstanceId()).isEqualTo(revealed.cardInstanceId());
        assertThat(wire.revealedCards().getFirst().cardType().name()).isEqualTo("SWING");
        assertThat(wire.revealedCards().getFirst().grade().name()).isEqualTo("III");
    }

    @Test
    void influenceTraced_mapsOnlyTheViewerTargetAndInfluencers() {
        var domain = new InfluenceTraced(
                UUID.randomUUID(), 2, 1, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()));

        var wire = mapper.toWire(domain);

        assertThat(wire.gameId()).isEqualTo(domain.gameId());
        assertThat(wire.eraNumber()).isEqualTo(domain.eraNumber());
        assertThat(wire.roundNumber()).isEqualTo(domain.roundNumber());
        assertThat(wire.playerId()).isEqualTo(domain.playerId());
        assertThat(wire.targetEventId()).isEqualTo(domain.targetEventId());
        assertThat(wire.influencerPlayerIds()).containsExactlyElementsOf(domain.influencerPlayerIds());
    }
}
