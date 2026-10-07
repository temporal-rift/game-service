package io.github.temporalrift.game.session.domain.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.session.domain.lobby.SeatingPlanMismatchException;
import io.github.temporalrift.game.shared.domain.model.Faction;

class FactionAssignmentTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Test
    @DisplayName("agreed factions are applied to the lobby players in lobby order")
    void fromAgreedFactions_coveringExactlyTheLobby_keepsLobbyOrder() {
        var assignments = FactionAssignment.fromAgreedFactions(
                List.of(SECOND, FIRST, THIRD),
                Map.of(FIRST, Faction.ERASERS, SECOND, Faction.PROPHETS, THIRD, Faction.WEAVERS));

        assertThat(assignments)
                .containsExactly(
                        new FactionAssignment(SECOND, Faction.PROPHETS),
                        new FactionAssignment(FIRST, Faction.ERASERS),
                        new FactionAssignment(THIRD, Faction.WEAVERS));
    }

    @Test
    @DisplayName("a lobby player without an agreed faction is rejected")
    void fromAgreedFactions_lobbyPlayerMissing_isRejected() {
        assertThatThrownBy(() -> FactionAssignment.fromAgreedFactions(
                        List.of(FIRST, SECOND, THIRD), Map.of(FIRST, Faction.ERASERS, SECOND, Faction.PROPHETS)))
                .isInstanceOf(SeatingPlanMismatchException.class);
    }

    @Test
    @DisplayName("an agreed seat that nobody joined is rejected")
    void fromAgreedFactions_seatWithoutLobbyPlayer_isRejected() {
        assertThatThrownBy(() -> FactionAssignment.fromAgreedFactions(
                        List.of(FIRST, SECOND),
                        Map.of(FIRST, Faction.ERASERS, SECOND, Faction.PROPHETS, THIRD, Faction.WEAVERS)))
                .isInstanceOf(SeatingPlanMismatchException.class);
    }

    @Test
    @DisplayName("a lobby player outside the agreed seats is rejected even when the counts match")
    void fromAgreedFactions_strangerInLobby_isRejected() {
        assertThatThrownBy(() -> FactionAssignment.fromAgreedFactions(
                        List.of(FIRST, SECOND, UUID.randomUUID()),
                        Map.of(FIRST, Faction.ERASERS, SECOND, Faction.PROPHETS, THIRD, Faction.WEAVERS)))
                .isInstanceOf(SeatingPlanMismatchException.class);
    }
}
