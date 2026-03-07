# ⏳ Temporal Rift — Saga Design

> Defines all sagas in the system: triggers, steps, wait conditions, and compensation flows.
> A saga is a long-running business transaction that spans multiple services or aggregates,
> coordinated through domain events rather than distributed transactions.

---

## Overview

| Saga | Scope | Service | Trigger | Key Complexity |
|---|---|---|---|---|
| `GameStartSaga` | Pre-game | game-service / session | `StartGame` command | Faction assignment atomicity |
| `EraSaga` | Per era | game-service / session | `EraStarted` | Orchestrates 3 child sagas |
| `ActionRoundSaga` | Per round | game-service / action | Round N opened | Timer vs all-submitted race condition |
| `ResolutionSaga` | Per era | timeline-service | `ResolutionStarted` | Action ordering, paradox branching |
| `ParadoxResolutionSaga` | Per paradox | timeline-service | `ParadoxDetected` | Nested within ResolutionSaga |
| `GameEndSaga` | End of game | game-service / session | Win / collapse / stabilize | Resource cleanup |
| `WeaverChainSaga` | Cross-era | timeline-service | `THREAD` special action | Only saga spanning multiple eras |
| `PlayerReconnectSaga` | Per disconnect | game-service / session | WebSocket disconnect | Grace timer, state restoration |

`EraSaga` is a **parent saga** — it spawns and awaits `ActionRoundSaga` × 3 and `ResolutionSaga` as children.
`WeaverChainSaga` is the only saga **completely decoupled from era boundaries** — it starts in one era and completes or dies in any future era.

---

## Saga 1 — `GameStartSaga`

**Service:** game-service → session module

**Trigger:** `StartGame` command from host player

### Steps

```
StartGame command
      │
      ▼
1. Validate lobby
   - 3–5 players present
   - all players connected
      │
      ▼
2. Draw N factions randomly from roster
      │
      ▼
3. Assign one faction per player
   - emit FactionAssigned × N  (private, one per player)
      │
      ▼
4. emit FactionsDrawn  (public)
      │
      ▼
5. emit GameStarted  (public)
      │
      ▼
6. Trigger EraSaga (era 1)
```

**Waits for:** nothing — all steps are system-driven, no player input required

### Compensation

| Failure | Compensation |
|---|---|
| Faction assignment fails midway | Unassign all already-assigned factions, emit `GameStartFailed`, return lobby to `WAITING` |
| Player disconnects during steps 1–5 | Cancel start, emit `GameStartCancelled` |

---

## Saga 2 — `EraSaga`

**Service:** game-service → session module (orchestrator)

**Trigger:** `EraStarted` event

### Steps

```
EraStarted
      │
      ▼
1. Draw 3 events from deck (+ cascaded carry-overs)
   - emit EventsDrawn
      │
      ▼
2. Deal 5 cards to each player
   - emit HandDealt × N  (private, one per player)
      │
      ▼
3. Run ActionRoundSaga  (round 1)
   - wait for ActionRoundClosed (round 1)
      │
      ▼
4. Run ActionRoundSaga  (round 2)
   - wait for ActionRoundClosed (round 2)
      │
      ▼
5. Run ActionRoundSaga  (round 3)
   - wait for ActionRoundClosed (round 3)
      │
      ▼
6. Trigger ResolutionSaga
   - wait for ScoresUpdated
      │
      ▼
7. Check win conditions
      │
      ├── winner found ──────────────────▶ Trigger GameEndSaga
      │
      └── no winner ────────────────────▶ emit EraEnded
                                           Trigger EraSaga (era N+1)
```

**Waits for:** `ActionRoundClosed` × 3 (sequentially), then `ScoresUpdated`

### Compensation

| Failure | Compensation |
|---|---|
| Deck runs out of events | Emit `GameEndedAbnormally` |
| `ResolutionSaga` fails | Emit `EraFailed`, trigger `GameEndedAbnormally` |

---

## Saga 3 — `ActionRoundSaga`

**Service:** game-service → action module

**Trigger:** previous `ActionRoundClosed` or `HandDealt` (for round 1)

### Steps

```
ActionRoundStarted  (emit with timer)
      │
      ├── player submits ──────────────────────────────────┐
      │   CardPlayed or SpecialActionPlayed                 │
      │   mark player as submitted                          │
      │   repeat until all submitted ───────────────────────┤
      │                                                     │
      └── timer expires ──────────────────────────────────▶ │
          emit ActionRoundTimerExpired                       │
          emit PlayerSkipped × missing players               │
                                                             ▼
                                                    ActionRoundClosed
                                                    (closedReason: ALL_SUBMITTED
                                                     or TIMER_EXPIRED)
                                                             │
                                                             ▼
                                                    RoundSummaryPublished
                                                    (filtered — categories only)
```

**Race condition:** both paths (all submitted / timer expires) converge to `ActionRoundClosed`.
The saga must handle both without double-closing. First trigger wins — second is ignored.

**Waits for:** `CardPlayed` / `SpecialActionPlayed` per player OR `ActionRoundTimerExpired`

### Compensation

| Failure | Compensation |
|---|---|
| Player submits after round closed | Reject silently, emit `LateSubmissionRejected` (private) |
| Timer service fails | Fallback close after 2× timeout, skip all remaining players |
| Player submits duplicate action | Reject, return error to player |

---

## Saga 4 — `ResolutionSaga`

**Service:** timeline-service

**Trigger:** `ResolutionStarted` event (fired by `game-service` session module after round 3 closes)

### Steps

```
ResolutionStarted
      │
      ▼
1. Collect all actions from rounds 1, 2, 3
   Apply card and special effects to FutureEvent aggregates
   (in submission order within each round)
      │
      ▼
2. emit ProbabilityStateCalculated
      │
      ▼
3. Run paradox detection — check all 4 paradox types
      │
      ├── no paradoxes ─────────────────────────────────────────────┐
      │                                                              │
      └── paradoxes found ──────────────────────────────────────┐   │
          emit ParadoxDetected                                   │   │
          Trigger ParadoxResolutionSaga                         │   │
          wait for ParadoxResolved or ParadoxCascaded           │   │
                                                                │   │
                    ParadoxResolved ──────────────────────────▶ │   │
                    ParadoxCascaded ──────────────────────────▶ │   │
                    (increment cascade counter in Game)         │   │
                                                                ▼   ▼
                                                        emit OutcomeApplied × events
                                                                │
                                                                ▼
                                                        emit ScoresUpdated
```

**Waits for:** `ParadoxResolved` or `ParadoxCascaded` per paradox (if any)

### Action Ordering Rules

Within a single round, actions are applied in this priority order:
1. `NULLIFY` — cancels the last card, applied first
2. `SEAL` — locks outcomes before other effects
3. `ANNIHILATE` — removes outcomes
4. `CORRUPT` — inverts other cards
5. `AMPLIFY` — doubles next card effect
6. All remaining cards in submission timestamp order

### Compensation

| Failure | Compensation |
|---|---|
| `ParadoxResolutionSaga` times out | Force cascade on all unresolved paradoxes |
| Probability state invalid after calculation (doesn't sum to 100) | Emit `ResolutionFailed`, trigger `GameEndedAbnormally` |
| Conflicting effects produce impossible state | Log, apply last-write-wins, emit `ResolutionWarning` |

---

## Saga 5 — `ParadoxResolutionSaga`

**Service:** timeline-service

**Trigger:** `ParadoxDetected` event

### Steps

```
ParadoxDetected
      │
      ▼
emit ParadoxResolutionPhaseStarted  (with timer)
      │
      ├── player submits resolution card ──────────────────────────┐
      │   ParadoxResolutionCardPlayed                               │
      │   (no faction specials allowed)                             │
      │   repeat until all submitted ────────────────────────────▶ │
      │                                                             │
      └── timer expires ──────────────────────────────────────────▶│
          skip non-submitted players                                │
                                                                    ▼
                                              Apply submitted cards to affected events
                                              Re-run paradox detection (affected events only)
                                                                    │
                                              ┌─────────────────────┴──────────────────────┐
                                              │                                             │
                                         paradox cleared                           paradox persists
                                              │                                             │
                                              ▼                                             ▼
                                     emit ParadoxResolved                       emit ParadoxCascaded
                                                                        (increment cascade counter in Game)
                                                                        (carry forward probability state)
```

**Waits for:** `ParadoxResolutionCardPlayed` per player OR timer expiry

### Compensation

| Failure | Compensation |
|---|---|
| Timer expires with missing submissions | Proceed with submitted cards only |
| All players skip | Force cascade immediately, emit `ParadoxCascaded` |
| Cascade counter reaches 3 | `Game` aggregate emits `TimelineCollapsed`, triggers `GameEndSaga` |

---

## Saga 6 — `GameEndSaga`

**Service:** game-service → session module

**Trigger:** `WinConditionMet`, `TimelineCollapsed`, or `TimelineStabilized`

### Steps

```
WinConditionMet / TimelineCollapsed / TimelineStabilized
      │
      ▼
1. Freeze game state
   - reject all further commands for this gameId
      │
      ▼
2. Calculate final results
   - if TimelineCollapsed: determine winners/losers per special ending rules
   - if TimelineStabilized: determine winners/losers per special ending rules
   - if WinConditionMet: single winner
      │
      ▼
3. emit GameEnded
      │
      ▼
4. emit FactionRevealed  (all factions now public)
      │
      ▼
5. Persist final game record
      │
      ▼
6. Release resources
   - cancel all active timers for this gameId
   - close WebSocket sessions
   - deregister Kafka consumer group offsets for this gameId
```

**Waits for:** nothing — all steps are system-driven

### Compensation

| Failure | Compensation |
|---|---|
| Final record persistence fails | Retry with exponential backoff — events already emitted so players have results regardless |
| Resource cleanup fails | Log and alert — non-blocking, game result already delivered |

---

## Saga 7 — `WeaverChainSaga`

**Service:** timeline-service

**Trigger:** `SpecialActionPlayed` where `specialAction = THREAD`

**Note:** This is the only saga that spans multiple eras. It remains open across era boundaries until completed, broken, or the game ends.

### Steps

```
THREAD special action played
      │
      ▼
1. Validate causal link
   - referenced past event must have actually resolved
   - referenced outcome must match what was recorded in OutcomeApplied
      │
      ├── invalid ──────────────▶ reject, emit ThreadRejected (private)
      │
      └── valid
            │
            ▼
      emit ChainLinkAdded
      chainLength++
            │
            ├── chainLength == 3 ──────────────▶ emit ChainCompleted
            │                                    saga ends (success)
            │
            └── chainLength < 3
                  │
                  ▼
            saga stays open
            wait for next THREAD action in any future era
            (or termination events below)
```

**Waits for:** next `SpecialActionPlayed` with `THREAD` from same player — across any future era

### Termination Conditions

| Event | Outcome |
|---|---|
| `UNRAVEL` targets this chain | Emit `ChainBroken`, saga ends (failure) |
| `ANNIHILATE` removes a linked outcome | Emit `ChainLinkInvalidated`, chain length decremented |
| `GameEnded` with chain length < 3 | Chain incomplete, no bonus, saga ends |
| `GameEnded` with chain length ≥ 3 | `ChainCompleted` already fired before game end |

### Compensation

| Failure | Compensation |
|---|---|
| Referenced past outcome not found | Reject Thread action, emit `ThreadRejected` |
| Chain invalidated mid-game | Decrement length, player may attempt to rebuild |

---

## Saga 8 — `PlayerReconnectSaga`

**Service:** game-service → session module

**Trigger:** Player WebSocket disconnects during an active game

### Steps

```
WebSocket disconnect detected
      │
      ▼
1. Mark player as DISCONNECTED in session
   Notify other players (PlayerDisconnected event)
      │
      ▼
2. Start grace timer (default: 30 seconds, configurable)
      │
      ├── player reconnects within grace period ──────────────────┐
      │                                                            │
      └── grace timer expires ──────────────────────────────┐    │
                                                             │    │
                                                             ▼    ▼
                                              ┌─────── reconnected? ───────┐
                                              │ yes                        │ no
                                              ▼                            ▼
                                   Restore session               Mark player ABANDONED
                                   Resend current state          Auto-skip for remaining rounds
                                   via projection-service        emit PlayerAbandoned
                                   Saga ends (success)                     │
                                                                            ▼
                                                               Only player remaining?
                                                                            │
                                                              ┌─────────────┴──────────────┐
                                                              │ yes                         │ no
                                                              ▼                             ▼
                                                    Trigger GameEndSaga           Continue game
                                                    (GameEndedAbnormally)         with remaining players
```

**Waits for:** reconnection event OR grace timer expiry

### Compensation

| Failure | Compensation |
|---|---|
| Player reconnects after `ABANDONED` | Reject reconnection — game already adapted |
| Multiple simultaneous disconnects | Run one `PlayerReconnectSaga` per player independently |
| All players disconnect | Trigger `GameEndSaga` with `GameEndedAbnormally` |

---

## Saga Nesting Map

```
GameStartSaga
      │
      └──▶ EraSaga (era 1)
                │
                ├──▶ ActionRoundSaga (round 1)
                ├──▶ ActionRoundSaga (round 2)
                ├──▶ ActionRoundSaga (round 3)
                │
                └──▶ ResolutionSaga
                          │
                          └──▶ ParadoxResolutionSaga (0..N per era)
                                    │
                                    └── on 3rd cascade ──▶ GameEndSaga

      EraSaga (era N) ──▶ EraSaga (era N+1) ... until GameEndSaga

WeaverChainSaga  ◀── runs independently across era boundaries
PlayerReconnectSaga  ◀── runs independently triggered by infrastructure events
```

---

*Compensation flows are intentional design — not error handling. In an event-driven system, compensation is how the domain responds to the real world not behaving as expected.*
