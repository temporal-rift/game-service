package io.github.temporalrift.game.action.domain.playerstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.CardNotInHandException;

@DisplayName("PlayerState")
class PlayerStateTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int MAX_HAND = 5;

    PlayerState newState() {
        return new PlayerState(UUID.randomUUID(), GAME_ID, PLAYER_ID);
    }

    PlayerState.CardInstance card(CardType type) {
        return new PlayerState.CardInstance(UUID.randomUUID(), type);
    }

    @Test
    @DisplayName("reconstitute does not register any events")
    void reconstituteRegistersNoEvents() {
        // when
        var ps = PlayerState.reconstitute(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS, List.of(), false);

        // then
        assertThat(ps.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("assignFaction — first assignment — stored correctly")
    void assignFactionFirstAssignment() {
        // given
        var ps = newState();

        // when
        ps.assignFaction(Faction.ERASERS);

        // then
        assertThat(ps.faction()).isEqualTo(Faction.ERASERS);
    }

    @Test
    @DisplayName("assignFaction — second assignment — throws FactionImmutableException")
    void assignFactionSecondAssignmentThrows() {
        // given
        var ps = newState();
        ps.assignFaction(Faction.ERASERS);

        // when / then
        assertThatExceptionOfType(FactionImmutableException.class).isThrownBy(() -> ps.assignFaction(Faction.PROPHETS));
    }

    @Test
    @DisplayName("dealCard — adds card to hand")
    void dealCardAddsToHand() {
        // given
        var ps = newState();
        var c = card(CardType.PUSH);

        // when
        ps.dealCard(c, MAX_HAND);

        // then
        assertThat(ps.hand()).containsExactly(c);
    }

    @Test
    @DisplayName("dealCard — hand at max — throws HandFullException")
    void dealCardHandAtMaxThrows() {
        // given
        var ps = newState();
        for (int i = 0; i < 5; i++) {
            ps.dealCard(card(CardType.PUSH), MAX_HAND);
        }

        // when / then
        assertThatExceptionOfType(HandFullException.class)
                .isThrownBy(() -> ps.dealCard(card(CardType.SUPPRESS), MAX_HAND));
    }

    @Test
    @DisplayName("removeCard — card in hand — removed from hand")
    void removeCardInHand() {
        // given
        var ps = newState();
        var c = card(CardType.JAM);
        ps.dealCard(c, MAX_HAND);

        // when
        ps.removeCard(c.cardInstanceId());

        // then
        assertThat(ps.hand()).doesNotContain(c);
    }

    @Test
    @DisplayName("removeCard — card not in hand — throws CardNotInHandException")
    void removeCardNotInHandThrows() {
        // given
        var ps = newState();

        // when / then
        assertThatExceptionOfType(CardNotInHandException.class).isThrownBy(() -> ps.removeCard(UUID.randomUUID()));
    }

    @Test
    @DisplayName("applyJam — isJammed becomes true")
    void applyJamSetsJammedTrue() {
        // given
        var ps = newState();

        // when
        ps.applyJam();

        // then
        assertThat(ps.isJammed()).isTrue();
    }

    @Test
    @DisplayName("clearJam — after jam applied — isJammed becomes false")
    void clearJamAfterApplyJam() {
        // given
        var ps = newState();
        ps.applyJam();

        // when
        ps.clearJam();

        // then
        assertThat(ps.isJammed()).isFalse();
    }

    @Test
    @DisplayName("hand() returns unmodifiable view")
    void handReturnsUnmodifiableView() {
        // given
        var ps = newState();

        // when / then
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ps.hand().add(card(CardType.PUSH)));
    }
}
