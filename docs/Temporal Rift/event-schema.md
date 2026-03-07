# ⏳ Temporal Rift — Event Schema & Kafka Topic Design

> Technical specification for all domain events and Kafka infrastructure.
> Lives alongside the GDD but evolves independently — schema changes do not require GDD updates and vice versa.

---

## 1. Event Envelope

Every event regardless of type is wrapped in this envelope:

```json
{
  "eventId": "uuid",
  "eventType": "string",
  "aggregateId": "uuid",
  "aggregateType": "string",
  "gameId": "uuid",
  "occurredAt": "ISO-8601",
  "version": 1,
  "payload": { }
}
```

| Field | Description |
|---|---|
| `eventId` | Unique identifier for this event instance. Used for idempotency. |
| `eventType` | Fully qualified event name e.g. `session.GameStarted` |
| `aggregateId` | ID of the aggregate that produced this event |
| `aggregateType` | Type of aggregate e.g. `Lobby`, `FutureEvent` |
| `gameId` | Always present — used as Kafka partition key to guarantee ordering within a game |
| `occurredAt` | ISO-8601 timestamp of when the event occurred |
| `version` | Schema version for this event type. Consumers must handle unknown versions gracefully. |
| `payload` | Event-specific data — defined per event type below |

---

## 2. Kafka Topics

### 2.1 Topic Design Principles

- One topic per bounded context — not one topic per event type
- Commands and events on separate topics — different retention, different consumer groups
- Partitioned by `gameId` — guarantees ordering of all events within a game
- Notification and Projection consume all domain event topics

### 2.2 Domain Event Topics

| Topic | Produced By | Consumed By | Partition Key |
|---|---|---|---|
| `game.events` | game-service | timeline-service, read-service | gameId |
| `timeline.events` | timeline-service | game-service, read-service | gameId |

### 2.3 Command Topics

| Topic | Produced By | Consumed By | Partition Key |
|---|---|---|---|
| `game.commands` | API gateway | game-service | lobbyId or gameId |
| `timeline.commands` | game-service | timeline-service | gameId |

### 2.4 Infrastructure Topics

| Topic | Purpose | Partition Key | Notes |
|---|---|---|---|
| `outbox.events` | Transactional outbox relay | gameId | All services write here before domain topics |
| `game.dlq` | Dead letter queue | gameId | Failed events from any service |

### 2.5 Retention Policy

| Topic Type | Retention | Reason |
|---|---|---|
| Domain event topics | 7 days | Replay and debugging |
| Command topics | 1 day | Commands are transient, not replayed |
| `outbox.events` | 1 hour | Processed immediately by relay |
| `game.dlq` | 30 days | Time to investigate failures |

---

## 3. Event Schemas

Schemas are defined by payload only — the envelope is always the same (see section 1).

Privacy annotations:
- 🌐 **Public** — delivered to all players
- 🔒 **Private** — delivered only to the target player
- 📢 **Filtered** — delivered to all but with fields redacted per visibility rules

---

### 3.1 Session & Lobby Events

#### `LobbyCreated` 🌐
```json
{
  "lobbyId": "uuid",
  "hostPlayerId": "uuid",
  "createdAt": "ISO-8601"
}
```

#### `PlayerJoinedLobby` 🌐
```json
{
  "lobbyId": "uuid",
  "playerId": "uuid",
  "playerName": "string"
}
```

#### `PlayerLeftLobby` 🌐
```json
{
  "lobbyId": "uuid",
  "playerId": "uuid"
}
```

#### `FactionsDrawn` 🌐
```json
{
  "gameId": "uuid",
  "lobbyId": "uuid",
  "factions": ["ERASERS", "PROPHETS", "WEAVERS"]
}
```

#### `FactionAssigned` 🔒
```json
{
  "gameId": "uuid",
  "playerId": "uuid",
  "faction": "ERASERS"
}
```
> Private — produced once per player, `read-service` notification module delivers only to the assigned player.

#### `GameStarted` 🌐
```json
{
  "gameId": "uuid",
  "lobbyId": "uuid",
  "playerIds": ["uuid", "uuid", "uuid"],
  "totalFactions": 3,
  "deckSize": 30
}
```

---

### 3.2 Era Start Events

#### `EraStarted` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "cascadedEventIds": ["uuid"]
}
```
> `cascadedEventIds` — events carried forward from previous era paradox cascades. Empty list on era 1.

#### `EventsDrawn` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "events": [
    {
      "eventId": "uuid",
      "title": "string",
      "outcomes": [
        { "outcomeId": "uuid", "description": "string", "initialProbability": 33 },
        { "outcomeId": "uuid", "description": "string", "initialProbability": 33 },
        { "outcomeId": "uuid", "description": "string", "initialProbability": 34 }
      ],
      "isCascaded": false
    }
  ]
}
```
> Probabilities sum to 100. Cascaded events carry their last known probability state — not reset to 33/33/34.

#### `HandDealt` 🔒
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "playerId": "uuid",
  "cards": [
    { "cardInstanceId": "uuid", "cardType": "PUSH" },
    { "cardInstanceId": "uuid", "cardType": "SCAN" },
    { "cardInstanceId": "uuid", "cardType": "JAM" },
    { "cardInstanceId": "uuid", "cardType": "COLLIDE" },
    { "cardInstanceId": "uuid", "cardType": "AMPLIFY" }
  ]
}
```
> Private — delivered only to the receiving player. `cardInstanceId` distinguishes two copies of the same card type in the same hand.

---

### 3.3 Action Round Events

#### `ActionRoundStarted` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "timerSeconds": 60,
  "pendingPlayerIds": ["uuid", "uuid", "uuid"]
}
```

#### `CardPlayed` 🔒
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "playerId": "uuid",
  "cardInstanceId": "uuid",
  "cardType": "PUSH",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid"
}
```
> Private in transit. Stored in full for resolution and Trace card purposes. `read-service` notification module never forwards raw CardPlayed to other players — only via `RoundSummaryPublished`.

#### `SpecialActionPlayed` 🔒
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "playerId": "uuid",
  "faction": "ERASERS",
  "specialAction": "ANNIHILATE",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid",
  "targetPlayerId": null
}
```
> `targetPlayerId` is null for event-targeting specials. Populated for player-targeting specials (Corrupt, Unravel, Jam, Expose).

#### `ActionRoundTimerExpired` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "missingPlayerIds": ["uuid"]
}
```

#### `PlayerSkipped` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "playerId": "uuid",
  "reason": "TIMER_EXPIRED"
}
```

#### `ActionRoundClosed` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "closedReason": "ALL_SUBMITTED",
  "totalActions": 3
}
```
> `closedReason`: `ALL_SUBMITTED` or `TIMER_EXPIRED`.

#### `RoundSummaryPublished` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "roundNumber": 1,
  "actionSummaries": [
    {
      "playerId": "uuid",
      "actionCategory": "PROBABILITY_SHIFTER",
      "actionFamily": "CARD",
      "skipped": false
    }
  ]
}
```
> Deliberately minimal — no targets, no specific card types. Only category (`PROBABILITY_SHIFTER`, `INFORMATION`, `DISRUPTION`, `PARADOX`) and family (`CARD` or `SPECIAL`).

---

### 3.4 Resolution Events

#### `ResolutionStarted` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1
}
```

#### `ProbabilityStateCalculated` 📢
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "eventStates": [
    {
      "eventId": "uuid",
      "outcomes": [
        { "outcomeId": "uuid", "probability": 45, "isAnnihilated": false, "isSealed": false },
        { "outcomeId": "uuid", "probability": 35, "isAnnihilated": false, "isSealed": false },
        { "outcomeId": "uuid", "probability": 20, "isAnnihilated": true, "isSealed": false }
      ]
    }
  ]
}
```
> Filtered — only the Scan card grants a player access to exact probabilities. All other players see event state without probability values.

#### `ParadoxDetected` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "paradoxes": [
    {
      "paradoxId": "uuid",
      "type": "DEAD_HEAT",
      "affectedEventId": "uuid",
      "affectedOutcomeIds": ["uuid", "uuid"],
      "description": "string"
    }
  ]
}
```

#### `ParadoxResolutionPhaseStarted` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "paradoxIds": ["uuid"],
  "timerSeconds": 60
}
```

#### `ParadoxResolutionCardPlayed` 🔒
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "playerId": "uuid",
  "cardInstanceId": "uuid",
  "cardType": "STABILIZE",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid"
}
```

#### `ParadoxResolved` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "paradoxId": "uuid",
  "resolvedByPlayerId": "uuid"
}
```

#### `ParadoxCascaded` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "paradoxId": "uuid",
  "affectedEventId": "uuid",
  "carryForwardProbabilityState": [
    { "outcomeId": "uuid", "probability": 50 },
    { "outcomeId": "uuid", "probability": 50 },
    { "outcomeId": "uuid", "probability": 0 }
  ]
}
```

#### `OutcomeApplied` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "eventId": "uuid",
  "winningOutcomeId": "uuid",
  "finalProbabilities": [
    { "outcomeId": "uuid", "probability": 65 },
    { "outcomeId": "uuid", "probability": 25 },
    { "outcomeId": "uuid", "probability": 10 }
  ]
}
```

#### `ScoresUpdated` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "updates": [
    {
      "playerId": "uuid",
      "faction": "PROPHETS",
      "pointsDelta": 4,
      "reason": "EVENT_RESOLVED_AS_WRITTEN",
      "newTotal": 12
    }
  ]
}
```

---

### 3.5 Era End & Game End Events

#### `EraEnded` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 1,
  "cascadedParadoxCount": 0,
  "nextEraNumber": 2
}
```

#### `WinConditionMet` 🌐
```json
{
  "gameId": "uuid",
  "winnerId": "uuid",
  "faction": "WEAVERS",
  "finalScore": 20,
  "winType": "SCORE_THRESHOLD"
}
```

#### `TimelineCollapsed` 🌐
```json
{
  "gameId": "uuid",
  "eraNumber": 3,
  "winners": [
    { "playerId": "uuid", "faction": "ERASERS" },
    { "playerId": "uuid", "faction": "REVISIONISTS" }
  ],
  "losers": [
    { "playerId": "uuid", "faction": "PROPHETS" },
    { "playerId": "uuid", "faction": "WEAVERS" }
  ]
}
```

#### `TimelineStabilized` 🌐
```json
{
  "gameId": "uuid",
  "winners": [
    { "playerId": "uuid", "faction": "PROPHETS" },
    { "playerId": "uuid", "faction": "WEAVERS", "activeChainLength": 3 }
  ],
  "losers": [
    { "playerId": "uuid", "faction": "ERASERS" },
    { "playerId": "uuid", "faction": "REVISIONISTS" }
  ]
}
```

#### `GameEnded` 🌐
```json
{
  "gameId": "uuid",
  "endReason": "SCORE_THRESHOLD",
  "finalScores": [
    { "playerId": "uuid", "faction": "WEAVERS", "score": 20 },
    { "playerId": "uuid", "faction": "ERASERS", "score": 14 }
  ]
}
```
> `endReason`: `SCORE_THRESHOLD`, `TIMELINE_COLLAPSED`, `TIMELINE_STABILIZED`.

#### `FactionRevealed` 🌐
```json
{
  "gameId": "uuid",
  "reveals": [
    { "playerId": "uuid", "faction": "ERASERS" },
    { "playerId": "uuid", "faction": "PROPHETS" },
    { "playerId": "uuid", "faction": "WEAVERS" }
  ]
}
```

---

### 3.6 Weaver Chain Events

#### `ChainLinkAdded` 🌐
```json
{
  "gameId": "uuid",
  "chainId": "uuid",
  "playerId": "uuid",
  "linkedEventId": "uuid",
  "linkedOutcomeId": "uuid",
  "chainLength": 2,
  "previousLinkEventId": "uuid"
}
```

#### `ChainCompleted` 🌐
```json
{
  "gameId": "uuid",
  "chainId": "uuid",
  "playerId": "uuid",
  "links": [
    { "eventId": "uuid", "outcomeId": "uuid", "eraNumber": 1 },
    { "eventId": "uuid", "outcomeId": "uuid", "eraNumber": 2 },
    { "eventId": "uuid", "outcomeId": "uuid", "eraNumber": 3 }
  ]
}
```

#### `ChainBroken` 🌐
```json
{
  "gameId": "uuid",
  "chainId": "uuid",
  "brokenByPlayerId": "uuid",
  "targetPlayerId": "uuid",
  "chainLengthAtBreak": 2
}
```

---

## 4. Card Types Reference

Valid values for `cardType` field:

| Value | Category |
|---|---|
| `PUSH` | PROBABILITY_SHIFTER |
| `SUPPRESS` | PROBABILITY_SHIFTER |
| `SWING` | PROBABILITY_SHIFTER |
| `AMPLIFY` | PROBABILITY_SHIFTER |
| `INTERCEPT` | INFORMATION |
| `SCAN` | INFORMATION |
| `TRACE` | INFORMATION |
| `DECOY` | INFORMATION |
| `JAM` | DISRUPTION |
| `STALL` | DISRUPTION |
| `REDIRECT` | DISRUPTION |
| `NULLIFY` | DISRUPTION |
| `COLLIDE` | PARADOX |
| `STABILIZE` | PARADOX |
| `DETONATE` | PARADOX |

**Enums:**
- `CardCategory`: `PROBABILITY_SHIFTER`, `INFORMATION`, `DISRUPTION`, `PARADOX`
- `CardType`: 15 values above, each with `getCategory()` method
- Convenience: `CardType.byCategory(CardCategory)` returns `Set<CardType>`

---

## 5. Faction & Special Action Reference

Valid values for `faction` and `specialAction` fields:

| Faction | Special Actions |
|---|---|
| `ERASERS` | `ANNIHILATE`, `CORRUPT`, `CASCADE` |
| `PROPHETS` | `FORESIGHT`, `SEAL`, `FULFILLMENT` |
| `REVISIONISTS` | `REWRITE`, `MIMIC`, `OBSCURE` |
| `WEAVERS` | `THREAD`, `TAPESTRY`, `UNRAVEL` |
| `ACTIVISTS` | `RALLY`, `EXPOSE`, `MOMENTUM` |

**Enums:**
- `Faction`: `ERASERS`, `PROPHETS`, `REVISIONISTS`, `WEAVERS`, `ACTIVISTS`
  - `getSpecialActions()` returns `Set<SpecialAction>`
  - `hasSpecialAction(SpecialAction)` returns boolean
- `SpecialAction`: 15 values above

---

## 6. Paradox Types Reference

Valid values for `paradox.type` field:

| Value | Description |
|---|---|
| `DEAD_HEAT` | Two outcomes reach equal probability |
| `IMPOSSIBLE_ERASURE` | Annihilated outcome has highest probability |
| `CHAIN_CONFLICT` | Two Weaver chains require opposite outcomes |
| `SEAL_BREACH` | Sealed outcome's probability was modified |

**Enum:** `ParadoxType`: `DEAD_HEAT`, `IMPOSSIBLE_ERASURE`, `CHAIN_CONFLICT`, `SEAL_BREACH`

---

## 7. Schema Evolution Policy

- Additive changes (new optional fields) → increment `version`, backwards compatible
- Breaking changes (removed or renamed fields) → new event type, old type deprecated with sunset date
- Consumers must ignore unknown fields
- Consumers must handle unknown `version` values gracefully by logging and skipping

---

*Schemas are intentionally defined as good-enough to start. Missing fields will be discovered during service implementation and added with a version increment.*
