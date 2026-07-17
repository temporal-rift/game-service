package io.github.temporalrift.game.shared.infrastructure.config;

import static org.mockito.BDDMockito.then;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.IncompleteEventPublications;

@ExtendWith(MockitoExtension.class)
class IncompleteEventPublicationResubmitterTest {

    @Mock
    IncompleteEventPublications incompletePublications;

    @InjectMocks
    IncompleteEventPublicationResubmitter resubmitter;

    @Test
    @DisplayName("resubmits only publications older than the in-flight grace window")
    void resubmitStale_usesAgeThreshold() {
        resubmitter.resubmitStale();

        then(incompletePublications).should().resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
    }
}
