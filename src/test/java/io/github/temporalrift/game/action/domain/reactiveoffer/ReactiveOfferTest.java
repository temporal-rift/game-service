package io.github.temporalrift.game.action.domain.reactiveoffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.shared.domain.model.CardType;

class ReactiveOfferTest {

    @Test
    void consume_acceptsEachOfferedCardExactlyOnce() {
        var stabilize = UUID.randomUUID();
        var detonate = UUID.randomUUID();
        var offer = new ReactiveOffer(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), stabilize, detonate);

        assertThat(offer.consume(stabilize)).isEqualTo(CardType.STABILIZE);
        assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.CONSUMED);
        assertThat(offer.consumedCardInstanceId()).isEqualTo(stabilize);
        assertThatThrownBy(() -> offer.consume(detonate)).isInstanceOf(CardNotInHandException.class);
    }

    @Test
    void consume_acceptsDetonate() {
        var offer = new ReactiveOffer(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(offer.consume(offer.detonateCardInstanceId())).isEqualTo(CardType.DETONATE);
        assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.CONSUMED);
    }

    @Test
    void cardTypeOf_rejectsUnknownCard() {
        var offer = new ReactiveOffer(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> offer.cardTypeOf(UUID.randomUUID())).isInstanceOf(CardNotInHandException.class);
    }

    @Test
    void consume_rejectsAfterExpiry() {
        var offer = new ReactiveOffer(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        offer.expire();

        assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.EXPIRED);
        assertThatThrownBy(() -> offer.consume(offer.stabilizeCardInstanceId()))
                .isInstanceOf(CardNotInHandException.class);
    }

    @Test
    void expire_leavesConsumedOfferUnchanged() {
        var offer = new ReactiveOffer(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        offer.consume(offer.stabilizeCardInstanceId());

        offer.expire();

        assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.CONSUMED);
    }

    @Test
    void constructor_rejectsIdenticalCardIds() {
        var card = UUID.randomUUID();

        assertThatThrownBy(
                        () -> new ReactiveOffer(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), card, card))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
