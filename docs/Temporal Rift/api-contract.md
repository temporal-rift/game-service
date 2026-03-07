# ⏳ Temporal Rift — API Contract

> Defines all REST endpoints and WebSocket connections exposed by each service.
> Auth: every request requires `Authorization: Bearer <jwt>` with `playerId` claim.
> Errors follow RFC 9457 Problem Details format.
> All REST endpoints versioned under `/api/v1`.

---

## Deployment Overview

| Service | Type | Internal Modules | Protocol | Notes |
|---|---|---|---|---|
| game-service | Spring Modulith | session, action, scoring | REST | Lobby, rounds, scoring |
| timeline-service | Spring Boot | — | REST | Core domain, event sourced |
| read-service | Spring Modulith | projection, notification | REST + WebSocket | Read models and push |

---

## 1. game-service

Hosts three internal modules: `session`, `action`, `scoring`. All exposed under the same deployable and base URL. Modules are internal separation — not visible to API consumers.

---

### Session Module Endpoints

#### `POST /api/v1/lobbies`
Create a new lobby. The requesting player becomes host.

**Request:**
```json
{
  "playerName": "string"
}
```

**Response `201`:**
```json
{
  "lobbyId": "uuid",
  "hostPlayerId": "uuid",
  "joinCode": "string"
}
```

---

#### `POST /api/v1/lobbies/{lobbyId}/join`
Join an existing lobby using its join code.

**Request:**
```json
{
  "playerName": "string"
}
```

**Response `200`:**
```json
{
  "lobbyId": "uuid",
  "playerId": "uuid",
  "currentPlayers": [
    { "playerId": "uuid", "playerName": "string", "isHost": true }
  ]
}
```

**Errors:** `404` lobby not found, `409` lobby already started, `422` lobby full (5 players maximum)

---

#### `DELETE /api/v1/lobbies/{lobbyId}/players/me`
Leave a lobby before game starts.

**Response `204`**

**Errors:** `403` host cannot leave without transferring host role, `404`

---

#### `POST /api/v1/lobbies/{lobbyId}/start`
Host starts the game. Triggers `GameStartSaga` asynchronously.

**Response `202`:**
```json
{
  "gameId": "uuid"
}
```

**Errors:** `403` not host, `422` fewer than 3 players

---

#### `GET /api/v1/games/{gameId}`
Get current game summary — public state only.

**Response `200`:**
```json
{
  "gameId": "uuid",
  "status": "IN_PROGRESS",
  "eraNumber": 2,
  "playerCount": 3,
  "cascadedParadoxCount": 1
}
```

---

### Action Module Endpoints

#### `POST /api/v1/games/{gameId}/rounds/{roundNumber}/actions`
Submit a card or faction special action for the current round. One submission per player per round.

**Request — card action:**
```json
{
  "actionType": "CARD",
  "cardInstanceId": "uuid",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid"
}
```

**Request — special action:**
```json
{
  "actionType": "SPECIAL",
  "specialAction": "ANNIHILATE",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid",
  "targetPlayerId": null
}
```
> `targetPlayerId` is null for event-targeting specials. Required for player-targeting specials (Corrupt, Unravel, Jam, Expose).

**Response `202`:**
```json
{
  "actionId": "uuid",
  "status": "SUBMITTED"
}
```

**Errors:** `409` round already closed, `409` player already submitted, `422` card not in hand, `422` player is Jammed, `422` round number mismatch, `422` invalid target

---

#### `POST /api/v1/games/{gameId}/paradox-resolution/actions`
Submit a single card during paradox resolution phase. Faction specials not allowed.

**Request:**
```json
{
  "cardInstanceId": "uuid",
  "targetEventId": "uuid",
  "targetOutcomeId": "uuid"
}
```

**Response `202`:**
```json
{
  "actionId": "uuid",
  "status": "SUBMITTED"
}
```

**Errors:** `409` not in paradox resolution phase, `422` faction specials not allowed, `409` already submitted

---

#### `GET /api/v1/games/{gameId}/rounds/{roundNumber}/status`
Get current round submission status — who has submitted, not what they submitted.

**Response `200`:**
```json
{
  "roundNumber": 1,
  "status": "OPEN",
  "timerRemainingSeconds": 42,
  "submittedCount": 2,
  "totalPlayers": 3,
  "pendingPlayerIds": ["uuid"]
}
```
> `status`: `OPEN` or `CLOSED`

---

### Scoring Module Endpoints

#### `GET /api/v1/games/{gameId}/scores`
Get current scores for all players. Factions hidden until `FactionRevealed` fires at game end.

**Response `200`:**
```json
{
  "gameId": "uuid",
  "eraNumber": 2,
  "scores": [
    { "playerId": "uuid", "playerName": "string", "score": 12, "faction": null },
    { "playerId": "uuid", "playerName": "string", "score": 8, "faction": null },
    { "playerId": "uuid", "playerName": "string", "score": 6, "faction": null }
  ]
}
```
> `faction` is `null` until game ends. After `FactionRevealed` it contains the faction name.

---

#### `GET /api/v1/games/{gameId}/scores/history`
Get full scoring history broken down by era and reason.

**Response `200`:**
```json
{
  "gameId": "uuid",
  "history": [
    {
      "eraNumber": 1,
      "deltas": [
        { "playerId": "uuid", "pointsDelta": 4, "reason": "EVENT_RESOLVED_AS_WRITTEN" },
        { "playerId": "uuid", "pointsDelta": -2, "reason": "EVENT_RESOLVED_DIFFERENTLY" }
      ]
    }
  ]
}
```

---

## 2. timeline-service

Plain Spring Boot — single bounded context. Primarily event-driven via Kafka. Exposes two restricted read endpoints gated by round state.

---

#### `GET /api/v1/games/{gameId}/events/{eventId}/probability-state`
Get exact probability state of a future event.

**Restricted:** only accessible to a player who played `SCAN` targeting this event in the current round.

**Response `200`:**
```json
{
  "eventId": "uuid",
  "outcomes": [
    { "outcomeId": "uuid", "description": "string", "probability": 45, "isAnnihilated": false, "isSealed": false },
    { "outcomeId": "uuid", "description": "string", "probability": 35, "isAnnihilated": false, "isSealed": false },
    { "outcomeId": "uuid", "description": "string", "probability": 20, "isAnnihilated": true, "isSealed": false }
  ]
}
```

**Errors:** `403` player has not played Scan targeting this event this round, `404` event not in current era

---

#### `GET /api/v1/games/{gameId}/chains`
Get active Weaver chain state. Restricted to the Weavers faction player only.

**Response `200`:**
```json
{
  "chainId": "uuid",
  "status": "ACTIVE",
  "chainLength": 2,
  "links": [
    { "eventId": "uuid", "outcomeId": "uuid", "eraNumber": 1 },
    { "eventId": "uuid", "outcomeId": "uuid", "eraNumber": 2 }
  ]
}
```
> `status`: `ACTIVE`, `COMPLETED`, `BROKEN`

**Errors:** `403` requesting player is not Weavers faction, `404` no chain exists yet

---

## 3. read-service

Spring Modulith with two internal modules: `projection` (REST) and `notification` (WebSocket). No domain logic — pure read and push.

---

### Projection Module Endpoints

#### `GET /api/v1/games/{gameId}/state`
Full current game state for the requesting player. Primary endpoint called on load and reconnect.

**Response `200`:**
```json
{
  "gameId": "uuid",
  "eraNumber": 2,
  "phase": "ACTION_ROUND_2",
  "myFaction": "ERASERS",
  "myHand": [
    { "cardInstanceId": "uuid", "cardType": "PUSH" },
    { "cardInstanceId": "uuid", "cardType": "NULLIFY" }
  ],
  "myScore": 12,
  "mySpecialActions": ["ANNIHILATE", "CORRUPT", "CASCADE"],
  "myJammedUntilRound": null,
  "activeEvents": [
    {
      "eventId": "uuid",
      "title": "string",
      "isCascaded": false,
      "outcomes": [
        { "outcomeId": "uuid", "description": "string" },
        { "outcomeId": "uuid", "description": "string" },
        { "outcomeId": "uuid", "description": "string" }
      ]
    }
  ],
  "players": [
    { "playerId": "uuid", "playerName": "string", "score": 8, "isConnected": true, "faction": null }
  ],
  "lastRoundSummary": {
    "roundNumber": 1,
    "actionSummaries": [
      { "playerId": "uuid", "actionCategory": "PROBABILITY_SHIFTER", "actionFamily": "CARD", "skipped": false }
    ]
  }
}
```

> `phase` values: `LOBBY`, `ERA_START`, `ACTION_ROUND_1`, `ACTION_ROUND_2`, `ACTION_ROUND_3`, `PARADOX_RESOLUTION`, `RESOLUTION`, `ERA_END`, `GAME_ENDED`

> `myJammedUntilRound` is null if not jammed, otherwise the round number until which faction specials are blocked.

> `faction` in players array is null until game ends. Probability values never included — only via `timeline-service` Scan endpoint.

---

#### `GET /api/v1/games/{gameId}/history`
Full era-by-era history of resolved outcomes and paradoxes.

**Response `200`:**
```json
{
  "gameId": "uuid",
  "eras": [
    {
      "eraNumber": 1,
      "outcomes": [
        {
          "eventId": "uuid",
          "title": "string",
          "winningOutcomeId": "uuid",
          "winningOutcomeDescription": "string"
        }
      ],
      "paradoxesCascaded": 0
    },
    {
      "eraNumber": 2,
      "outcomes": [],
      "paradoxesCascaded": 1,
      "cascadedEvents": [
        { "eventId": "uuid", "title": "string" }
      ]
    }
  ]
}
```

---

### Notification Module — WebSocket

#### `WS /ws/games/{gameId}`
Per-player WebSocket session for real-time game events. Receive-only — all game actions go through REST.

**Connection:**
```
WS /ws/games/{gameId}?token=<jwt>
```

**On connect:** server immediately pushes current game state snapshot sourced from projection module.

**Message format — server to client:**
```json
{
  "type": "EVENT",
  "eventType": "ActionRoundStarted",
  "occurredAt": "ISO-8601",
  "payload": { }
}
```

**Message format — client to server:**
```json
{
  "type": "PING"
}
```

### Per-Player Filtering Rules

| Event | Rule |
|---|---|
| `FactionAssigned` | Delivered only to the assigned player |
| `HandDealt` | Delivered only to the receiving player |
| `CardPlayed` | Never forwarded to any player |
| `SpecialActionPlayed` | Never forwarded to any player |
| `ProbabilityStateCalculated` | Delivered only to players who played Scan this round |
| `ParadoxResolutionCardPlayed` | Never forwarded to any player |
| `FactionRevealed` | Delivered to all — game is over |
| All other events | Delivered to all players in the game |

---

## Error Format

All REST errors follow RFC 9457 Problem Details:

```json
{
  "type": "https://temporal-rift.example/errors/round-already-closed",
  "title": "Round Already Closed",
  "status": 409,
  "detail": "Action round 2 is already closed for game 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "instance": "/api/v1/games/3fa85f64/rounds/2/actions"
}
```

---

## Phase Reference

| Phase | Description |
|---|---|
| `LOBBY` | Game not yet started |
| `ERA_START` | Era beginning, events being drawn, hands dealt |
| `ACTION_ROUND_1` | First action round open |
| `ACTION_ROUND_2` | Second action round open |
| `ACTION_ROUND_3` | Third action round open |
| `PARADOX_RESOLUTION` | Paradox resolution phase open |
| `RESOLUTION` | System resolving outcomes |
| `ERA_END` | Era closed, checking win conditions |
| `GAME_ENDED` | Game over |

---

*API contracts are intentionally minimal — only what is needed to implement the first working version. Additional endpoints will be added as implementation surfaces gaps.*
