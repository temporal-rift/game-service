package com.temporalrift.game;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GameServiceApplicationIT {

    @Test
    void contextLoads() {
        var modules = ApplicationModules.of(GameServiceApplication.class);

        modules.verify();

        new Documenter(modules)
                .writeModulesAsPlantUml(DiagramOptions.defaults())
                .writeIndividualModulesAsPlantUml(DiagramOptions.defaults())
                .writeModuleCanvases(CanvasOptions.defaults());
    }
}
