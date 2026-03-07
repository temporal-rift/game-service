# ⏳ Temporal Rift — Game Design Document

> A multiplayer hidden-faction strategy game designed as a backend architecture showcase.

---

## 1. Concept

3–5 players, each a time traveler belonging to a **secret faction**. Players can see future events before they happen and must act in the past to influence their outcomes. Actions conflict, creating **paradoxes** that must be resolved before time moves forward.

Every game mechanic maps intentionally to an event-driven microservices pattern.

---

## 2. Players & Factions

Each game uses **exactly as many factions as there are players** (3–5), drawn randomly from the roster of 5. Each player is assigned exactly one faction secretly at game start. No two players share a faction. No player knows another's faction unless revealed by card effects.

### 2.1 Faction Roster

| Faction | Goal | Style | Win Condition |
|---|---|---|---|
| ⚡ Erasers | Wipe outcomes completely | Aggressive, chaotic | Annihilate 4+ outcomes OR reach 20pts |
| 📜 Prophets | Events resolve as written | Preservationist | 5+ events resolve as written OR reach 20pts |
| 🔄 Revisionists | Secret preferred outcome changes each era | Flexible, unpredictable | Secret outcome wins 3+ eras OR reach 20pts |
| 🧵 Weavers | Build causal chains across eras | Long-term strategist | Complete a chain of 3+ connected events OR reach 20pts |
| 📣 Activists | Publicly declare outcome, score double if correct | High risk / high reward | Correct declarations in 3+ consecutive eras OR reach 20pts |

### 2.2 Faction Special Actions

Each faction has **3 unique special actions** always available regardless of hand. These define faction identity and cannot be drawn as cards. Players play 1 card OR 1 special per action round, never both.

#### ⚡ Erasers
- **Annihilate** — remove one outcome from an event entirely. That outcome cannot win this era regardless of probability.
- **Corrupt** — target another player's card played this round; invert its probability effect.
- **Cascade** — if an outcome is erased this era, carry the erasure to the same outcome next era automatically.

#### 📜 Prophets
- **Foresight** — look at next era's future events before anyone else. Only you know them this round.
- **Seal** — lock one outcome's current probability so no cards or actions can modify it until end of round.
- **Fulfillment** — publicly declare before resolution: if the written outcome wins, your score doubles for that event.

#### 🔄 Revisionists
- **Rewrite** — swap secret preferred outcome to any available outcome. Others see a revision happened but not what changed.
- **Mimic** — copy the probability effect of any card played by another player this round.
- **Obscure** — for one round, faction identity is hidden even from deduction effects; appears as a random other faction.

#### 🧵 Weavers
- **Thread** — declare a causal link between current and a past event outcome. If consistent, chain grows and probability shifts in their favor.
- **Tapestry** — protect the next chain link from Eraser's Annihilate once per era (requires active chain of 2+).
- **Unravel** — break another player's established chain, resetting their chain progress.

#### 📣 Activists
- **Rally** — publicly declare an outcome at round start. All probability shifts this round are boosted 50% toward that outcome.
- **Expose** — reveal in detail another player's actions from last round: target event and probability shift direction.
- **Momentum** — if declared outcome won last era, carry a +10% probability bonus into this era's declaration automatically.

---

## 3. Card System

All players share the same card pool. At the start of each era each player draws **5 cards**. Cards are played during action rounds — 1 card OR 1 faction special per round, never both.

### Group 1 — Probability Shifters

| Card | Effect | Notes |
|---|---|---|
| Push | +20% to one outcome | Basic offensive card |
| Suppress | -20% to one outcome | Basic defensive card |
| Swing | Move 30% from one outcome to another on same event | Powerful redirect |
| Amplify | Double effect of next card played this round | Combo enabler |

### Group 2 — Information

| Card | Effect | Notes |
|---|---|---|
| Intercept | See one card from another player's hand secretly | Espionage |
| Scan | See current probability state of one event (normally hidden) | Intel gathering |
| Trace | See which player influenced a specific event last round | Deduction tool |
| Decoy | Play a fake action visible to others but with no effect | Bluffing tool |

### Group 3 — Disruption

| Card | Effect | Notes |
|---|---|---|
| Jam | Target player cannot use special actions next round | Faction suppression |
| Stall | Delay resolution of one event by one round | Buys time |
| Redirect | Move another player's last probability shift to a different outcome | Counter-play |
| Nullify | Cancel the last card played by any player this round | Hard counter |

### Group 4 — Paradox

| Card | Effect | Notes |
|---|---|---|
| Collide | Force two outcomes to equal probability, guaranteeing a paradox | Chaos card |
| Stabilize | Prevent a paradox from triggering on one event this round | Defensive utility |
| Detonate | If a paradox exists, double its negative score effect on all players except you | High risk |

---

## 4. Future Events

### 4.1 Structure
- Each future event has exactly **3 possible outcomes**
- Events are pre-written cards in a **finite deck of 30**
- Drawn randomly each era, no repeats within a game
- If a Cascade carries an event forward, it re-enters at the top of the next era's draw

### 4.2 Probability Model
- Each outcome starts at **33% probability** (equal baseline)
- Floor: **0%** — ceiling: **90%** (no outcome can be guaranteed)
- All 3 outcomes always sum to **100%**
- Shifting one outcome redistributes proportionally across the other two

### 4.3 Information Visibility

| Information | Visibility |
|---|---|
| Future event names and outcomes | Public — all players |
| Other players' action types after each round | Public — category only, no targets |
| Final resolved outcome | Public — all players |
| Your own faction | Private — only you |
| Your own hand | Private — only you |
| Exact probability state of events | Private — revealed only via Scan card |
| Who targeted what specifically | Private — revealed only via Trace card |
| Another player's hand | Private — revealed only via Intercept card |

---

## 5. Era Structure

Each era (round) follows this sequence:

| # | Phase | Description |
|---|---|---|
| 1 | Reveal | 3 future events drawn and shown to all players with their possible outcomes |
| 2 | Action Round 1 | Players secretly submit 1 card or 1 faction special. Limited info — act on instinct |
| 3 | Action Round 2 | Players see category and type of actions others played in Round 1. Adapt. |
| 4 | Action Round 3 | Decisive round. Partial probability shifts from Round 2 are visible. Final positioning. |
| 5 | Paradox Check | System detects paradoxes. If none: skip to Outcome. If yes: open Paradox Resolution. |
| 6 | Paradox Resolution | All players play one final card (no faction specials). Paradox resolves or cascades. |
| 7 | Outcome | Final probabilities determine winning outcomes. Scores updated per faction rules. |
| 8 | Era End | Check win conditions. Advance to next era or trigger game end. |

Each timed phase has a configurable timer. Default: **60 seconds per action round**.

---

## 6. Paradox System

Paradoxes occur when contradictory conditions exist simultaneously on the same event at resolution time.

### 6.1 Paradox Types

| Type | Trigger | Description |
|---|---|---|
| Type 1 — Dead Heat | Two outcomes reach equal probability | The timeline cannot decide. Most common. |
| Type 2 — Impossible Erasure | Annihilated outcome has highest probability | Eraser removed it but others pushed it anyway. |
| Type 3 — Chain Conflict | Two Weaver chains require opposite outcomes in same era | Two causal chains are mutually exclusive. |
| Type 4 — Seal Breach | A Sealed outcome's probability was modified | Prophet Seal bypassed via Redirect or Amplify edge case. |

> More paradox types will emerge through playtesting as faction special combinations interact in unexpected ways.

### 6.2 Paradox Resolution Flow

1. Paradox detected after Round 3
2. Paradox Resolution Phase opens — all players play exactly 1 card, no faction specials
3. **If resolves** → continue to Outcome phase normally
4. **If persists** → Cascade triggered: event produces no outcome, score zeroed for that event, event carries forward to next era with paradox state intact

---

## 7. Scoring

All values are provisional. Balance will be determined through playtesting.

| Faction | Scoring Rule | Points |
|---|---|---|
| ⚡ Erasers | Successfully annihilated outcome | +3 |
| | Corrupted card that inverted an opponent's effect | +2 |
| | Era ends with fewer outcomes than it started | +5 |
| 📜 Prophets | Event resolves exactly as written | +4 |
| | Fulfillment declared and succeeds | +8 |
| | Event resolves differently than written | -2 |
| 🔄 Revisionists | Secret preferred outcome wins this era | +4 |
| | No player correctly identified their faction this era | +6 |
| | Successful Mimic contributed to outcome winning | +2 |
| 🧵 Weavers | Event added to an active chain | +2 |
| | Completed chain of 3 connected events | +10 |
| | Chain broken by Unravel | -3 |
| 📣 Activists | Declared outcome wins with Rally active | +8 |
| | Declared outcome wins without Rally | +4 |
| | Expose visibly changed another player's behavior | +2 |

---

## 8. Win Conditions & Game End

### 8.1 Normal Victory
First player to reach **20 points** wins immediately.

### 8.2 Global End Triggers
- **3 Cascaded Paradoxes** — timeline collapses → special ending
- **5 Eras pass** with no winner — timeline stabilizes → special ending

### 8.3 Special Endings

| Faction | Timeline Collapse (3 Paradoxes) | Timeline Stabilization (5 Eras) |
|---|---|---|
| ⚡ Erasers | WIN — thrive in chaos | LOSE |
| 📜 Prophets | LOSE — need stability | WIN — written timeline survived |
| 🔄 Revisionists | WIN — thrive in chaos | LOSE |
| 🧵 Weavers | LOSE — need stability | WIN if active chain of 3+ exists |
| 📣 Activists | WIN only if they declared the collapsing event correctly | LOSE |

---

## 9. Domain Events

All things that happen in the system — past tense, immutable facts.

### Session & Lobby
| Event | Description |
|---|---|
| `LobbyCreated` | A player creates a lobby |
| `PlayerJoinedLobby` | A player joins an existing lobby |
| `PlayerLeftLobby` | A player leaves before game starts |
| `FactionsDrawn` | N factions randomly selected for this game |
| `FactionAssigned` | A faction secretly assigned to a specific player |
| `GameStarted` | Host starts the game, no more joins |

### Era Start
| Event | Description |
|---|---|
| `EraStarted` | Era N begins |
| `EventsDrawn` | 3 future events drawn from deck for this era |
| `HandDealt` | Each player receives 5 cards |

### Action Rounds (×3 per era)
| Event | Description |
|---|---|
| `ActionRoundStarted` | Round opens, timer starts |
| `CardPlayed` | Player submits a card action |
| `SpecialActionPlayed` | Player submits a faction special |
| `ActionRoundTimerExpired` | Timer ran out before all players submitted |
| `PlayerSkipped` | Player auto-skipped after timer expiry |
| `ActionRoundClosed` | All players submitted or timer expired |
| `RoundSummaryPublished` | Action types (no targets) published to all players |

### Resolution
| Event | Description |
|---|---|
| `ResolutionStarted` | All 3 rounds complete, resolution begins |
| `ProbabilityStateCalculated` | Final probabilities computed for all 3 events |
| `ParadoxDetected` | One or more paradox conditions found |
| `ParadoxResolutionPhaseStarted` | Paradox exists, resolution phase opens |
| `ParadoxResolutionCardPlayed` | Player submits their single resolution card |
| `ParadoxResolved` | Paradox cleared after resolution phase |
| `ParadoxCascaded` | Paradox persisted, event carries forward to next era |
| `OutcomeApplied` | Winning outcome recorded for each event |
| `ScoresUpdated` | All faction scoring rules evaluated and points applied |

### Era End & Game End
| Event | Description |
|---|---|
| `EraEnded` | Era closes, win conditions checked |
| `WinConditionMet` | A player reached 20 points |
| `TimelineCollapsed` | 3rd cascaded paradox triggered |
| `TimelineStabilized` | 5th era ended with no winner |
| `GameEnded` | Game over, final state recorded |
| `FactionRevealed` | All factions revealed to all players at game end |

### Weaver Chain
| Event | Description |
|---|---|
| `ChainLinkAdded` | Weaver successfully threads a causal link |
| `ChainCompleted` | Weaver chain reaches 3 connected events |
| `ChainBroken` | Another player Unravels a Weaver chain |

---

## 10. Commands

| Command | Issued By | Produces |
|---|---|---|
| `CreateLobby` | 👤 Player | `LobbyCreated` |
| `JoinLobby` | 👤 Player | `PlayerJoinedLobby` |
| `LeaveLobby` | 👤 Player | `PlayerLeftLobby` |
| `StartGame` | 👤 Host | `FactionsDrawn` → `FactionAssigned` ×N → `GameStarted` |
| `StartEra` | 🔄 Game saga | `EraStarted` → `EventsDrawn` → `HandDealt` ×N |
| `StartActionRound` | 🔄 Era saga | `ActionRoundStarted` |
| `PlayCard` | 👤 Player | `CardPlayed` |
| `PlaySpecialAction` | 👤 Player | `SpecialActionPlayed` |
| `CloseActionRound` | ⏱️ Timer or 🔄 all submitted | `ActionRoundClosed` → `RoundSummaryPublished` |
| `StartResolution` | 🔄 Era saga | `ResolutionStarted` |
| `CalculateProbabilities` | ⚙️ System | `ProbabilityStateCalculated` |
| `DetectParadoxes` | ⚙️ System | `ParadoxDetected` or → `OutcomeApplied` |
| `StartParadoxResolution` | ⚙️ System | `ParadoxResolutionPhaseStarted` |
| `PlayParadoxResolutionCard` | 👤 Player | `ParadoxResolutionCardPlayed` |
| `ResolveParadox` | ⚙️ System | `ParadoxResolved` or `ParadoxCascaded` |
| `ApplyOutcome` | ⚙️ System | `OutcomeApplied` |
| `UpdateScores` | ⚙️ System | `ScoresUpdated` |
| `EndEra` | 🔄 Era saga | `EraEnded` |
| `CheckWinConditions` | ⚙️ System | `WinConditionMet` / `TimelineCollapsed` / `TimelineStabilized` / next era |
| `EndGame` | ⚙️ System | `GameEnded` → `FactionRevealed` ×N |

> 👤 Player &nbsp;&nbsp; ⚙️ System &nbsp;&nbsp; ⏱️ Timer &nbsp;&nbsp; 🔄 Saga

---

## 11. Aggregates

### `Lobby`
**Owns:** player list, lobby status, host identity

**Invariants:**
- Cannot start with fewer than 3 or more than 5 players
- Cannot start if factions not assigned to all players
- Cannot join a lobby that already started
- Host cannot leave without transferring or closing lobby
- Each player assigned exactly one faction, no two players same faction

### `Game`
**Owns:** era counter, cascaded paradox counter, game status, deck state

**Invariants:**
- Cannot start a new era if game is over
- Cascaded paradox counter triggers `TimelineCollapsed` exactly at 3
- Era counter triggers `TimelineStabilized` exactly at 5
- Deck must have enough events remaining (3 per era minus carry-overs)

### `Era`
**Owns:** the 3 future events for this era, action round counter, current phase, carried-over cascaded events

**Invariants:**
- Always exactly 3 events active per era including carry-overs
- Cannot advance to next round if current round not closed
- Cannot start resolution if not all 3 action rounds completed
- Hand must be dealt before first action round opens

### `ActionRound`
**Owns:** submitted actions per player, round number, timer state, player submission status

**Invariants:**
- Each player submits exactly 1 action per round (card OR special, never both)
- Cannot submit after round is closed
- Cannot play a card not in your hand
- Cannot play a faction special if Jammed
- Round closes when all players submitted OR timer expires, whichever first

### `FutureEvent`
**Owns:** event identity, 3 outcomes, current probability state, annihilated outcomes, sealed outcomes, active Weaver chain links

**Invariants:**
- All 3 outcome probabilities always sum to 100%
- No single outcome exceeds 90% or falls below 0%
- Annihilated outcome cannot win even at highest probability (triggers Type 2 paradox)
- Sealed outcome probability cannot be modified (triggers Type 4 paradox if breached)
- Exactly one outcome wins per era unless cascaded

### `PlayerState`
**Owns:** player identity, assigned faction, current hand, score, active effects (Jammed, Obscured)

**Invariants:**
- Hand cannot exceed 5 cards; card removed from hand on play
- Score cannot go below 0
- Faction identity immutable once assigned
- Jammed player cannot play faction specials that round

### `WeaverChain` *(only exists if Weavers are in the game)*
**Owns:** chain of causally linked event outcomes across eras, chain length, chain status

**Invariants:**
- Chain links must reference real past outcomes that actually resolved
- Chain cannot grow if broken
- Completion requires exactly 3 consistent linked outcomes
- Only one active chain per Weaver player at a time

---

## 12. Bounded Contexts & Services

Three independently deployed services. Each owns its domain completely — separate repo, separate database, separate deployment.

### `game-service` — Spring Modulith
**Modules:** session, action, scoring

| Module | Aggregates | Responsibility | Key Patterns |
|---|---|---|---|
| session | `Lobby`, `Game` | Game lifecycle, faction assignment, era orchestration | Saga, Outbox |
| action | `ActionRound`, `PlayerState` (hand) | Round collection, timer race condition | Saga, Outbox |
| scoring | `PlayerState` (score) | Points, win conditions | Event listener, Outbox |

**Publishes:** `GameStarted`, `EraStarted`, `EraEnded`, `GameEnded`, `FactionAssigned`, `FactionRevealed`, `ActionRoundStarted`, `CardPlayed`, `SpecialActionPlayed`, `ActionRoundClosed`, `RoundSummaryPublished`, `ScoresUpdated`, `WinConditionMet`, `TimelineCollapsed`, `TimelineStabilized`

**Consumes:** `OutcomeApplied`, `ParadoxCascaded`, `ChainCompleted`, `ChainBroken`

> `PlayerState` is intentionally split across modules — action module owns hand/jam state, scoring module owns score. Different lifecycles, different reasons to change. Modules communicate via `ApplicationEvent` internally, never by direct method calls.

---

### `timeline-service` — plain Spring Boot
**Aggregates:** `FutureEvent`, `WeaverChain`

**Responsibility:** core domain. All probability math, paradox detection, outcome resolution, Weaver chain logic. The most complex service and the hot path during resolution.

**Key patterns:** Aggregate invariant enforcement, Event Sourcing, Long-running Saga (WeaverChain spans multiple eras), Outbox

**Publishes:** `EventsDrawn`, `ProbabilityStateCalculated`, `ParadoxDetected`, `ParadoxResolved`, `ParadoxCascaded`, `OutcomeApplied`, `ChainLinkAdded`, `ChainCompleted`, `ChainBroken`

**Consumes:** `EraStarted`, `CardPlayed`, `SpecialActionPlayed`, `ActionRoundClosed`

---

### `read-service` — Spring Modulith
**Modules:** projection, notification

| Module | Responsibility | Key Patterns |
|---|---|---|
| projection | CQRS read models, per-player projections, REST queries | Event consumer, denormalized read models |
| notification | WebSocket push, per-player event filtering | Event consumer, WebSocket sessions |

**Consumes:** all events from all services

**Produces:** nothing to Kafka — WebSocket push only from notification module

---

## 13. Context Map

```
Session ──────────────────────────────────────────────────┐
   │  GameStarted, EraStarted, EraEnded, GameEnded         │
   ▼                                                       │
Action ──── CardPlayed, SpecialActionPlayed ─────────────▶ Timeline
              ActionRoundClosed                             │
                                                            │ OutcomeApplied
                                                            │ ParadoxCascaded
                                                            │ ChainCompleted
                                                            │ ChainBroken
                                                            ▼
                                                         Scoring
                                                            │
                                                            │ ScoresUpdated
                                                            │ WinConditionMet
                                                            │ TimelineCollapsed
                                                            │ TimelineStabilized
                                                            ▼
Notification  ◀──────────────── ALL EVENTS ──────────────────
Projection    ◀──────────────── ALL EVENTS ──────────────────
```

> All inter-service communication via Kafka. No direct service-to-service calls. Outbox pattern on all state-changing write operations.

---

## 14. Open Questions (To Resolve in Playtesting)

- Is the 20-point threshold right? May need tuning per player count.
- Are 5 cards per era enough or do players run out too quickly?
- Does the 3-round action structure create enough interaction or does it feel slow?
- Are there faction special combinations that produce undiscovered paradox types?
- Is the Activists faction too punishing when their declaration fails?
- Does the Cascade mechanic create interesting tension or just frustration?

---

*All scoring values are provisional. Balance will be determined through playtesting iterations.*
