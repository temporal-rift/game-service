package com.temporalrift.game;

import org.springframework.boot.SpringApplication;

public class TestGameServiceApplication {

    static void main(String[] args) {
        SpringApplication.from(GameServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
