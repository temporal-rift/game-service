package io.github.temporalrift.game.shared.infrastructure.config;

import static org.mockito.BDDMockito.then;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.IncompleteEventPublications;

@ExtendWith(MockitoExtension.class)
class IncompleteEventPublicationResubmitterTest {

    @Mock
    IncompleteEventPublications incompletePublications;

    @Test
    @DisplayName("resubmits only publications older than the configured age threshold")
    void resubmitStale_usesAgeThreshold() {
        var resubmitter = new IncompleteEventPublicationResubmitter(incompletePublications, Duration.ofMinutes(2));

        resubmitter.resubmitStale();

        then(incompletePublications).should().resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(2));
    }
}
