# ⏳ Temporal Rift — Developer Notes

> Written for any developer (human or AI) picking up this codebase.
> Answers the "why" behind every non-obvious decision before you ask.
> Read this before reading any service code.

---

## 1. Project Purpose

This is not a production game platform. It is a **portfolio backend project** designed to showcase:

- Hexagonal Architecture (Ports & Adapters)
- Domain-Driven Design (aggregates, bounded contexts, value objects)
- CQRS (separate command and query models)
- Event Sourcing (state rebuilt from events, not from current DB rows)
- Saga pattern (long-running distributed transactions with compensation)
- Outbox pattern (guaranteed event delivery without distributed transactions)
- Kafka as the integration backbone between services

Every architectural decision is made to demonstrate these patterns correctly, not to optimize for simplicity or delivery speed. If something seems over-engineered for a game, that is intentional.

---

## 2. Deployment Architecture

Three deployables. Not six. This is a deliberate decision — not a shortcut.

| Service | Type | Internal Modules | Reason |
|---|---|---|---|
| `game-service` | Spring Modulith | session, action, scoring | Three tightly related contexts, similar scaling profile |
| `timeline-service` | Spring Boot | — | Single context, hot path, independent scaling need |
| `read-service` | Spring Modulith | projection, notification | Both are read/fan-out, no domain logic, scale together |

**Why not 6 microservices?** Each of the original 6 bounded contexts is genuinely distinct, but session/action/scoring are operationally coupled — they run during the same game phases and have similar load profiles. Splitting them would add distributed systems overhead (network calls, eventual consistency) without any scaling benefit. The module boundary inside `game-service` enforces the same separation that a service boundary would, at zero operational cost.

**Why not one monolith?** `timeline-service` has a genuinely different scaling profile — it is the hot path during resolution when every card play hits it simultaneously. `read-service` scales with WebSocket connections independently of write traffic. These are real reasons to deploy separately.

**Why Spring Modulith for game-service and read-service?** Each has 2-3 bounded contexts internally. Modulith enforces the boundaries at compile/test time and provides the outbox infrastructure. Plain Spring Boot for `timeline-service` because it has exactly one bounded context — Modulith would add a dependency with nothing to enforce.

---

## 3. Repository Structure

Four independent repositories — one per service plus the shared event contract library.
Each repo is independently deployable, independently versioned, and has its own CI/CD pipeline.

```
temporal-rift-domain-events/        ← shared library, published to GitHub Packages
├── src/main/java/com/temporalrift/events/
│   ├── session/                    ← LobbyCreated, GameStarted, EraStarted...
│   ├── action/                     ← CardPlayed, ActionRoundClosed...
│   ├── timeline/                   ← OutcomeApplied, ParadoxDetected...
│   ├── scoring/                    ← ScoresUpdated, WinConditionMet...
│   └── envelope/                   ← EventEnvelope record
└── pom.xml

temporal-rift-game-service/         ← Spring Modulith
├── docs/                           ← all 5 design docs live here (source of truth)
├── src/main/java/com/temporalrift/game/
│   ├── session/
│   ├── action/
│   └── scoring/
├── infrastructure/
│   ├── docker-compose.yml          ← local Kafka, PostgreSQL
│   └── docker-compose.test.yml
└── pom.xml

temporal-rift-timeline-service/     ← plain Spring Boot
├── src/main/java/com/temporalrift/timeline/
├── infrastructure/
│   ├── docker-compose.yml
│   └── docker-compose.test.yml
└── pom.xml

temporal-rift-read-service/         ← Spring Modulith
├── src/main/java/com/temporalrift/read/
│   ├── projection/
│   └── notification/
├── infrastructure/
│   ├── docker-compose.yml
│   └── docker-compose.test.yml
└── pom.xml
```

**Why 4 repos and not 3?** The `domain-events` library is the shared event contract between all services. It must be versioned independently — when an event schema changes, each service upgrades on its own schedule. If it lived inside one of the service repos, the others would have a compile-time dependency on that service's repo which breaks operational independence.

**Why docs live in `game-service`?** The docs describe the full system but someone has to own them. `game-service` is the natural owner as it hosts the session orchestration — the entry point of the system. Alternatively docs can live in their own repo (`temporal-rift-docs`) if the team prefers.

---

## 3. Hexagonal Architecture — How It Is Applied Here

The hexagonal package structure is the same in all three services. The difference is that Modulith services repeat it once per module.

### `timeline-service` — single bounded context, plain Spring Boot

```
com.temporalrift.timeline/
├── domain/
│   ├── model/          ← aggregates, entities, value objects
│   ├── event/          ← domain events (produced by aggregates)
│   ├── port/
│   │   ├── in/         ← use case interfaces (driven by outside world)
│   │   └── out/        ← repository and messaging interfaces (drive outside world)
│   └── exception/      ← domain exceptions
├── application/
│   ├── command/        ← command handlers (implement port.in)
│   ├── query/          ← query handlers (implement port.in)
│   └── saga/           ← saga orchestrators
├── infrastructure/
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── rest/   ← Spring controllers
│   │   │   └── kafka/  ← Kafka consumers
│   │   └── out/
│   │       ├── persistence/  ← JPA repositories, implementing port.out
│   │       └── kafka/        ← Kafka producers, implementing port.out
│   └── config/         ← Spring configuration, beans
└── shared/
    └── mapper/         ← MapStruct mappers between layers
```

### `game-service` — three modules, Spring Modulith

Each module has its own complete hexagonal structure. Modules communicate only through Spring `ApplicationEvents` — never by direct method calls across module boundaries.

```
com.temporalrift.game/
├── session/                        ← Modulith module
│   ├── domain/
│   │   ├── model/                  ← Lobby, Game aggregates
│   │   ├── event/                  ← LobbyCreated, GameStarted, EraStarted...
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   │   └── exception/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   └── saga/                   ← GameStartSaga, EraSaga, GameEndSaga, PlayerReconnectSaga
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   ├── rest/
│       │   │   └── kafka/
│       │   └── out/
│       │       ├── persistence/
│       │       └── kafka/
│       └── config/
├── action/                         ← Modulith module
│   ├── domain/
│   │   ├── model/                  ← ActionRound, PlayerState (hand) aggregates
│   │   ├── event/                  ← CardPlayed, ActionRoundClosed...
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   │   └── exception/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   └── saga/                   ← ActionRoundSaga (timer race condition lives here)
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   ├── rest/
│       │   │   └── kafka/
│       │   └── out/
│       │       ├── persistence/
│       │       └── kafka/
│       └── config/
├── scoring/                        ← Modulith module
│   ├── domain/
│   │   ├── model/                  ← PlayerState (score) aggregate
│   │   ├── event/                  ← ScoresUpdated, WinConditionMet...
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   │   └── exception/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   └── saga/
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   ├── rest/
│       │   │   └── kafka/
│       │   └── out/
│       │       ├── persistence/
│       │       └── kafka/
│       └── config/
└── shared/                         ← cross-module shared utilities only, no domain logic
    └── mapper/

```

### `read-service` — two modules, Spring Modulith

```
com.temporalrift.read/
├── projection/                     ← Modulith module
│   ├── domain/
│   │   ├── model/                  ← read models (not aggregates — no invariants)
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   ├── application/
│   │   └── query/                  ← query handlers, projection builders
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   ├── rest/
│       │   │   └── kafka/          ← consumes all domain events to build projections
│       │   └── out/
│       │       └── persistence/
│       └── config/
├── notification/                   ← Modulith module
│   ├── domain/
│   │   ├── model/                  ← WebSocket session registry, filtering rules
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   ├── application/
│   │   └── command/                ← filter and push events to correct players
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/
│       │   │   ├── websocket/      ← WebSocket handler (replaces rest/ here)
│       │   │   └── kafka/          ← consumes all domain events
│       │   └── out/
│       │       └── websocket/      ← pushes filtered events to player sessions
│       └── config/
└── shared/
    └── mapper/
```

**The golden rule:** the `domain` package has zero Spring annotations and zero infrastructure imports. It is plain Java. If you find yourself importing anything from `org.springframework` or `jakarta.persistence` inside `domain/`, stop — you are in the wrong layer.

**The Modulith rule:** modules communicate only through `ApplicationEvent` — never by injecting a bean from another module's `application` or `infrastructure` layer. Only a module's top-level public API classes may be referenced from another module.

---

## 4. Key Technology Decisions

### Spring Boot 4 + Java 25
- Use **records** for value objects, events, and DTOs — immutability by default
- Use **sealed interfaces** for discriminated unions (e.g. action types, paradox types)
- Use **virtual threads** (Project Loom) — enable with `spring.threads.virtual.enabled=true`
- Take advantage of **Java 25 features** like string templates, structured concurrency, and pattern matching

### Kafka
- **One topic per bounded context** — not one topic per event type
- **Partition key is always `gameId`** — guarantees ordering of all events within a game
- Events are serialized as **JSON** (not Avro) to keep the setup simple for a solo project
- Schema evolution via the `version` field in the envelope — consumers must ignore unknown fields

### PostgreSQL
- Each service owns its own schema — no cross-service joins ever
- **Event store table** in `timeline-service` and `session-service` for event-sourced aggregates
- **Outbox table** in every service that produces Kafka events

### Outbox Pattern
Every service that writes to the database and needs to produce a Kafka event **must** use the outbox pattern:
1. Write aggregate state + outbox record in the **same local transaction**
2. A separate outbox relay process polls the outbox table and publishes to Kafka
3. On successful publish, mark the outbox record as processed

Never publish directly to Kafka inside a database transaction. If the transaction commits and Kafka publish fails, you have lost the event permanently.

### Idempotency
Every Kafka consumer **must** be idempotent. Events can be delivered more than once (at-least-once delivery). Use `eventId` from the envelope to detect and skip duplicates. Store processed `eventId` values in a `processed_events` table per service.

---

## 5. Saga Implementation Notes

Sagas are implemented as **stateful Spring components** persisted to the database, not as in-memory state. A saga instance can survive a service restart.

Each saga has:
- A unique `sagaId` (UUID)
- A `gameId` it belongs to
- A `status` (STARTED, WAITING, COMPLETED, COMPENSATING, FAILED)
- A `currentStep` tracking where it is in the flow
- A serialized `context` blob with all data needed to continue or compensate

**Saga state transitions must be persisted before emitting events.** If you emit an event and then fail to persist the saga state update, you will process the same event twice and produce duplicate downstream events.

### The ActionRoundSaga Race Condition
This is the trickiest saga in the system. Two things can close a round:
1. All players submit
2. Timer expires

Both paths must converge to exactly one `ActionRoundClosed` event. Implementation approach:
- Use a database row-level lock on the `ActionRound` aggregate when closing
- First closer (timer or last submission) acquires the lock and sets status to `CLOSING`
- Second closer sees `CLOSING` status and exits without emitting
- Only one `ActionRoundClosed` is ever emitted per round

Never rely on Kafka ordering alone to solve this — the race exists at the application level.

### WeaverChainSaga Lifetime
This saga starts in one era and may live for up to 4 more eras (game max is 5). It must survive:
- Service restarts
- Era transitions
- The Weaver player disconnecting and reconnecting

Store the full chain state in the database. On reconnect, the player's state is restored from `projection-service` which reads from the chain state.

---

## 6. CQRS Notes

The write side (commands) and read side (queries) are separated at the service level:

- `timeline-service`, `action-service`, `session-service` own the **write side**
- `projection-service` owns the **read side**
- `scoring-service` is a hybrid — it writes score state from events but also serves score reads

**Never query the write-side database from a read endpoint.** If a controller needs data, it either reads from `projection-service` or from the service's own read model. Write-side tables are not optimized for reads and their schema should be allowed to change independently.

**Eventual consistency is expected and correct.** After a command is processed, the read model in `projection-service` will be slightly behind. This is acceptable. The client should not expect read-your-writes consistency — it receives real-time updates via WebSocket instead.

---

## 7. Event Sourcing Notes

`timeline-service` is the primary event-sourced service. `FutureEvent` and `WeaverChain` aggregates are rebuilt by replaying their event history, not by reading a current-state row.

Event store table schema (per aggregate type):
```sql
CREATE TABLE event_store (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type    VARCHAR(200) NOT NULL,
    event_version INT NOT NULL,
    payload       JSONB NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    sequence_nr   BIGINT NOT NULL
);

CREATE UNIQUE INDEX ON event_store (aggregate_id, sequence_nr);
```

`sequence_nr` is the optimistic concurrency control mechanism — if two processes try to append event with the same `sequence_nr` for the same `aggregate_id`, one will get a unique constraint violation and must retry.

**Snapshots:** for long-lived aggregates (WeaverChain across 5 eras), implement snapshots after every 20 events to avoid full replay on every load. Snapshot table:
```sql
CREATE TABLE aggregate_snapshot (
    aggregate_id   UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    snapshot_data  JSONB NOT NULL,
    sequence_nr    BIGINT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);
```

---

## 8. notification-service Notes

This service has no database. It is stateless — it holds WebSocket sessions in memory and filters/forwards events.

**Session registry:** use Redis to store the mapping of `playerId → WebSocket session node` so that in a multi-instance deployment, any node can look up which node holds a player's connection and route accordingly. For a single-instance deployment this is an in-memory map.

**Filtering logic** is the most important code in this service. The visibility rules defined in `event-schema.md` section 3 must be strictly enforced here. Any leak of private information (faction assignments, hands, raw card plays) to the wrong player is a game-breaking bug.

**Never store game state here.** If the service restarts, players reconnect and receive state from `projection-service`. `notification-service` is a dumb pipe.

---

## 9. projection-service Notes

This service builds and maintains **one read model per player per game**. It listens to all domain events and updates its denormalized view.

The most important projection is `PlayerGameStateProjection` — the response model for `GET /api/v1/games/{gameId}/state`. This must be rebuilt correctly from events in the right order.

**Event ordering matters.** Because partitions are per `gameId`, all events for a game arrive in order within a partition. Process them strictly in offset order. Never process events out of order.

**On service restart**, the service replays all events from Kafka (or from the event offset it last committed) to rebuild projections. This means projections are derived state — they can always be rebuilt from scratch if corrupted.

---

## 10. Testing Strategy

Each service has three test layers:

### Unit Tests
- Test domain logic in isolation — aggregates, value objects, saga steps
- No Spring context, no database, no Kafka
- Fast — should run in under 5 seconds total per service

### Integration Tests
- Test a single service end-to-end with real database (Testcontainers PostgreSQL)
- Test Kafka producers and consumers with real Kafka (Testcontainers Kafka)
- No other services — mock their Kafka events with test fixtures from `temporal-rift-domain-events` test utilities

### Architecture Tests (ArchUnit)
Every service must include these ArchUnit rules:
```java
// Domain has no Spring or infrastructure dependencies
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

// Application layer does not depend on infrastructure
noClasses().that().resideInAPackage("..application..")
    .should().dependOnClassesThat()
    .resideInAPackage("..infrastructure..");

// Ports are only implemented by adapters
classes().that().implement(resideInAPackage("..port.out.."))
    .should().resideInAPackage("..infrastructure.adapter.out..");
```

These tests enforce hexagonal boundaries automatically and catch accidental layer violations before they become habits.

---

## 11. Local Development Setup

Each service repo has its own `infrastructure/docker-compose.yml`. To run locally:

```bash
# Start this service's infrastructure (Kafka, PostgreSQL)
docker-compose -f infrastructure/docker-compose.yml up -d

# Run the service
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run architecture tests only
./mvnw test -Dtest="*ArchitectureTest"
```

**Note:** Requires Java 25+ and Maven 3.9+. All services use Spring Boot 4.

To run the full system locally, start the infrastructure in each repo separately. Kafka is shared — start it from any one service's docker-compose and point the others at the same instance via environment variables.

Kafka UI available at `http://localhost:8080` (via kafka-ui in docker-compose).
PostgreSQL: each service gets its own database on `localhost:5432`.

---

## 12. Implementation Order

Implement in this order. Each deployable is independently testable before the next one starts. Within `game-service`, implement modules in the order listed.

| Order | Deliverable | Module | Why this order |
|---|---|---|---|
| 1 | `game-service` | session module | Simplest saga, establishes patterns for all others |
| 2 | `game-service` | action module | Introduces timer race condition — solve this early |
| 3 | `game-service` | scoring module | Pure event listener — straightforward, completes game-service |
| 4 | `timeline-service` | — | Core domain — do fourth when patterns are established in game-service |
| 5 | `read-service` | projection module | Build read models from real events produced by 1–4 |
| 6 | `read-service` | notification module | Last — needs real events to filter and forward |

Do not skip ahead. `read-service` built before `timeline-service` is working will be built against mock events that may not match reality.

---

## 13. Common Mistakes to Avoid

**Publishing Kafka events outside a transaction**
Always use the outbox pattern. Never call `kafkaTemplate.send()` directly from a command handler.

**Querying across service boundaries**
If `action-service` needs player info from `session-service`, it listens to `FactionAssigned` and stores what it needs locally. It never calls `session-service`'s API.

**Putting business logic in sagas**
Sagas orchestrate — they do not decide. Business rules (invariants, validations) belong in aggregates. A saga step should be "send this command" or "wait for this event", not "if score > 20 then...".

**Mutable domain events**
Domain events are immutable facts. Once emitted they never change. If you find yourself wanting to update an event, you need a new corrective event instead.

**Sharing a database between services**
Each service has its own database schema. No joins across service schemas. No shared tables. If you need data from another service, consume its events.

**Ignoring the `version` field**
Consumers must check `version` and handle unknown versions gracefully (log and skip). Schema evolution will happen — do not assume version 1 forever.

**Saga compensation as error handling**
Compensation is not a try-catch. It is a deliberate business flow for when the world does not cooperate. Design compensation paths with the same care as happy paths.

---

## 14. Glossary

| Term | Definition in this project |
|---|---|
| Era | One round of the game — draw events, 3 action rounds, resolution, scoring |
| Future Event | A game event with 3 possible outcomes that players try to influence |
| Outcome | One of 3 possible results for a Future Event |
| Faction | A player's secret role with unique win conditions and special actions |
| Paradox | A contradictory state on a Future Event that must be resolved before outcomes are applied |
| Cascade | A paradox that could not be resolved — the event carries forward to the next era |
| Chain | A Weaver's sequence of 3 causally linked event outcomes across eras |
| Saga | A long-running business transaction coordinated through domain events |
| Outbox | A database table used to guarantee event delivery to Kafka |
| Projection | A denormalized read model built from domain events for query purposes |
| Aggregate | A cluster of domain objects treated as a single consistency boundary |
| Bounded Context | A service boundary where a specific domain language applies consistently |

---

*If something in the code contradicts this document, the code is wrong — not this document.
If something in this document contradicts the GDD, the GDD is the source of truth for game rules.*
