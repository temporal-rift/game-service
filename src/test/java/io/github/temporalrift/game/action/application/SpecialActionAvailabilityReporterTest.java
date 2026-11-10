package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.actionround.SpecialActionUnavailableReason;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.SpecialActionAvailabilityReported;
import io.github.temporalrift.game.action.domain.event.SpecialActionAvailabilityReported.SpecialAvailability;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository;
import io.github.temporalrift.game.action.domain.port.out.SpecialActionEraUsageRepository;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@ExtendWith(MockitoExtension.class)
class SpecialActionAvailabilityReporterTest {

    private static final UUID GAME_ID = UUID.randomUUID();

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    SpecialActionEraUsageRepository eraUsageRepository;

    @Mock
    SealGameUsageRepository sealGameUsageRepository;

    @Mock
    ActionEventPublisher publisher;

    @Mock
    GameRulesPort gameRules;

    SpecialActionAvailabilityReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new SpecialActionAvailabilityReporter(
                playerStateRepository,
                eraUsageRepository,
                sealGameUsageRepository,
                publisher,
                gameRules,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(gameRules.maxEras()).thenReturn(5);
        org.mockito.Mockito.lenient().when(gameRules.annihilateEarliestRound()).thenReturn(2);
        org.mockito.Mockito.lenient().when(gameRules.sealMaxUsesPerGame()).thenReturn(2);
        org.mockito.Mockito.lenient()
                .when(gameRules.onceEraBudgetedSpecials())
                .thenReturn(Set.of(SpecialAction.ANNIHILATE, SpecialAction.SEAL));
    }

    private static PlayerState player(Faction faction) {
        var state = new PlayerState(UUID.randomUUID(), GAME_ID, UUID.randomUUID());
        if (faction != null) {
            state.assignFaction(faction);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private List<SpecialActionAvailabilityReported> published() {
        var captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(publisher).should(org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream()
                .map(envelope -> (ActionEventPayload) envelope.payload())
                .map(SpecialActionAvailabilityReported.class::cast)
                .toList();
    }

    @Test
    @DisplayName("publishes one report per active player with the era's claimed specials and Seal count applied")
    void publishesOneReportPerActivePlayer() {
        var eraser = player(Faction.ERASERS);
        var prophet = player(Faction.PROPHETS);
        given(playerStateRepository.findAllByGameId(GAME_ID)).willReturn(List.of(eraser, prophet));
        given(eraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, 1, eraser.playerId()))
                .willReturn(Optional.of(SpecialActionEraUsage.reconstitute(
                        UUID.randomUUID(), GAME_ID, 1, eraser.playerId(), Set.of(SpecialAction.ANNIHILATE))));
        given(eraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, 1, prophet.playerId()))
                .willReturn(Optional.empty());
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, eraser.playerId()))
                .willReturn(0);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, prophet.playerId()))
                .willReturn(2);

        reporter.report(GAME_ID, 1, 3, List.of(eraser.playerId(), prophet.playerId()));

        var reports = published();
        assertThat(reports).hasSize(2);
        var eraserReport = reports.stream()
                .filter(report -> report.playerId().equals(eraser.playerId()))
                .findFirst()
                .orElseThrow();
        assertThat(eraserReport.roundNumber()).isEqualTo(3);
        assertThat(eraserReport.specials())
                .contains(new SpecialAvailability(
                        SpecialAction.ANNIHILATE, false, SpecialActionUnavailableReason.ERA_BUDGET_EXHAUSTED))
                .contains(new SpecialAvailability(SpecialAction.CORRUPT, true, null));
        var prophetReport = reports.stream()
                .filter(report -> report.playerId().equals(prophet.playerId()))
                .findFirst()
                .orElseThrow();
        assertThat(prophetReport.specials())
                .contains(new SpecialAvailability(
                        SpecialAction.SEAL, false, SpecialActionUnavailableReason.GAME_BUDGET_EXHAUSTED));
    }

    @Test
    @DisplayName("publishes nothing for an abandoned player or one without a faction")
    void skipsAbandonedAndFactionless() {
        var abandoned = player(Faction.ERASERS);
        var factionless = player(null);
        var active = player(Faction.WEAVERS);
        given(playerStateRepository.findAllByGameId(GAME_ID)).willReturn(List.of(abandoned, factionless, active));
        given(eraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, 1, active.playerId()))
                .willReturn(Optional.empty());
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, active.playerId()))
                .willReturn(0);

        reporter.report(GAME_ID, 1, 1, List.of(factionless.playerId(), active.playerId()));

        assertThat(published())
                .extracting(SpecialActionAvailabilityReported::playerId)
                .containsExactly(active.playerId());
        then(publisher).should(org.mockito.Mockito.never()).publishInternally(any());
    }

    @Test
    @DisplayName("reports a jammed player's three specials as JAMMED")
    void jammedPlayer() {
        var jammed = player(Faction.REVISIONISTS);
        jammed.applyJam();
        given(playerStateRepository.findAllByGameId(GAME_ID)).willReturn(List.of(jammed));
        given(eraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, 1, jammed.playerId()))
                .willReturn(Optional.empty());
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, jammed.playerId()))
                .willReturn(0);

        reporter.report(GAME_ID, 1, 3, List.of(jammed.playerId()));

        assertThat(published().getFirst().specials())
                .hasSize(3)
                .allSatisfy(special ->
                        assertThat(special.unavailableReason()).isEqualTo(SpecialActionUnavailableReason.JAMMED));
    }
}
