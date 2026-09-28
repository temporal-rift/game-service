package io.github.temporalrift.game.scoring.domain.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactionIdentification")
class FactionIdentificationTest {

    static final UUID REVISIONIST = UUID.randomUUID();
    static final UUID ACTIVIST = UUID.randomUUID();
    static final UUID WEAVER = UUID.randomUUID();

    @Test
    @DisplayName("a disclosed player is identified at any player count")
    void disclosedPlayerIsIdentified() {
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 5, Set.of(REVISIONIST)))
                .isTrue();
    }

    @Test
    @DisplayName("three players — one disclosed opponent lets the third player eliminate the Revisionist")
    void threePlayers_oneDisclosureIdentifies() {
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 3, Set.of())).isFalse();
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 3, Set.of(ACTIVIST)))
                .isTrue();
    }

    @Test
    @DisplayName("four players — two disclosed opponents are needed")
    void fourPlayers_twoDisclosuresIdentify() {
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 4, Set.of(ACTIVIST)))
                .isFalse();
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 4, Set.of(ACTIVIST, WEAVER)))
                .isTrue();
    }

    @Test
    @DisplayName("five players — disclosing both the Activist and the Weaver cannot eliminate the Revisionist")
    void fivePlayers_activistAndWeaverAreNotEnough() {
        assertThat(FactionIdentification.isIdentified(REVISIONIST, 5, Set.of(ACTIVIST, WEAVER)))
                .isFalse();
    }
}
