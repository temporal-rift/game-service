package io.github.temporalrift.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GameServiceApplicationIT {

    @Test
    void contextLoads() {
        var modules = ApplicationModules.of(GameServiceApplication.class);

        modules.verify();
        assertThat(modules.getModuleByName("action"))
                .as("action module must be detected by Spring Modulith")
                .isPresent();

        new Documenter(modules)
                .writeModulesAsPlantUml(DiagramOptions.defaults())
                .writeIndividualModulesAsPlantUml(DiagramOptions.defaults())
                .writeModuleCanvases(CanvasOptions.defaults());
    }
}
