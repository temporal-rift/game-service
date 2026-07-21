# Temporal Rift Game Orchestration Service

Spring Modulith service that owns the full game lifecycle: lobby management, game sessions, action rounds, and scoring.

## Requirements

- Java 26+
- Maven 3.9.13+
- Docker (for Testcontainers and local infrastructure)

## Local infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL and Kafka. Kafka UI available at `http://localhost:8080`.

## Deployment configuration

The default runtime profile intentionally has no insecure fallbacks for external services. Set these environment variables in every non-Docker deployment (for example, Kubernetes or Cloud Foundry):

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL login user |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL login password |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| `KAFKA_SECURITY_PROTOCOL` | Kafka transport protocol, such as `SASL_SSL` |
| `JWT_ISSUER_URI` | Trusted OAuth2/OpenID Connect issuer URI |

`docker-compose.yml` supplies local Docker values. Store credentials in the deployment platform's secret store rather than in application configuration. If a required variable is missing, startup fails with a diagnostic that identifies the variable and the required configuration contract.

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
