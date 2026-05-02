# Temporal Rift Game Orchestration Service

Spring Modulith service that owns the full game lifecycle: lobby management, game sessions, action rounds, and scoring.

## Requirements

- Java 25+
- Maven 3.9.13+
- Docker (for Testcontainers and local infrastructure)

## Local infrastructure

```bash
docker-compose -f infrastructure/docker-compose.yml up -d
```

Starts PostgreSQL and Kafka. Kafka UI available at `http://localhost:8080`.

## Build and test

```bash
# All tests (unit + integration via Testcontainers)
mvn test

# Single test class
mvn test -Dtest="LobbyTest"

# Architecture tests only
mvn test -Dtest="*ArchitectureTest"

# Integration test (Testcontainers PostgreSQL + Kafka + Modulith verify)
mvn test -Dtest="GameServiceApplicationIT"

# Check and fix formatting
mvn spotless:apply

# Run all quality gates (formatting + Checkstyle)
mvn validate
```

## Module structure

```
src/main/java/io/github/temporalrift/game/
├── session/    ← lobby + game lifecycle
├── action/     ← action round processing
├── scoring/    ← score tracking
└── shared/     ← cross-cutting concerns
```

Each module follows a hexagonal layout: `domain/` → `application/` → `infrastructure/`.
Modules communicate only via Spring `ApplicationEvent` — never by direct cross-module bean injection.

## Dependencies

- Parent BOM: `temporal-rift-bom:1.0.14` (Spotless, Checkstyle, OpenAPI generator)
- Shared events: `domain-events:1.0.7`
