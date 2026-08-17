# Task 9 Brief — Emergency inventory transactions and death-protection routes

Build the pure inventory routing/state-machine layer. Runtime packet sending belongs to Task 18.

## Route priority

1. Protection already in either hand -> no inventory mutation (`AlreadyInHand`).
2. Protection in another hotbar slot -> one carried-slot selection route to main hand (`HotbarSelect`).
3. Protection elsewhere in player inventory -> one vanilla container `SWAP` into either the selected hotbar slot (main hand) or offhand button `40`.
4. If an offhand shield is currently active, prefer main-hand destination so the active block state is preserved.
5. Otherwise prefer offhand so the player's selected main-hand item remains untouched.

Never hard-code menu slot ids. `MenuSlotMap` maps player inventory indices to the current menu's slot ids. Player inventory index `40` is offhand; hotbar indices are `0..8`, but current menu slot ids are provided by the map.

## Pure types

- `InventorySnapshot`: selected hotbar index, immutable slot snapshots keyed by player inventory index, active-offhand-shield flag.
- `InventorySlotSnapshot`: player inventory index, stable stack identity/key, count, death-protection flag, empty flag.
- `MenuSlotMap`: container id, state id, immutable inventory-index -> menu-slot mapping; `menuSlotForInventoryIndex(int)` returns optional int.
- `DeathProtectionRoute` sealed interface exactly as locked in the plan:
  - `AlreadyInHand(Destination)`
  - `HotbarSelect(int hotbarIndex)`
  - `ContainerSwap(int sourceMenuSlot, int button, Destination destination)`
  - `Destination { MAIN_HAND, OFF_HAND }`
- `DeathProtectionRoutePlanner#choose(InventorySnapshot, MenuSlotMap)` returns `Optional<DeathProtectionRoute>`.
- `EmergencyInventoryTransaction` immutable state machine with states `PLANNED`, `SENT`, `AWAITING_RECONCILE`, `CONFIRMED`, `CONTRADICTED`, `CONSUMED`, `RESTORING`, `DONE`.

## Transaction semantics

Track route, container id/state id, source/destination stack identities, send/deadline ticks, and whether the original destination stack is still restorable.

- `markSent()` -> SENT.
- `observeStateIdMismatch()` from SENT -> AWAITING_RECONCILE. In 26.1.2 this does **not** mean the click failed; a valid stale-state-id click is applied and then the server sends full state.
- `reconcile(authoritativeSource, authoritativeDestination)` -> CONFIRMED when the authoritative stacks match the expected swap, otherwise CONTRADICTED.
- Container-id/menu invalidation -> CONTRADICTED.
- Restoration is not attempted while a lethal-threat grace flag is true.
- `markConsumed()` -> CONSUMED and permanently invalidates restoration of the saved destination stack.
- Restoration only progresses from a confirmed, unconsumed transaction after authoritative state is consistent.

## RED tests

- protection hotbar slot 5, selected 1 -> `HotbarSelect(5)`.
- active offhand shield + inventory protection -> `ContainerSwap` destination MAIN_HAND using the current menu slot id and selected hotbar button.
- no shield + inventory protection -> offhand destination with button 40.
- missing player-inventory/menu mapping -> no unsafe route.
- stale state id -> AWAITING_RECONCILE, then matching full authoritative state -> CONFIRMED.
- contradictory reconciliation -> CONTRADICTED.
- restoration remains CONFIRMED while lethal threat still pending.
- consumed protection makes `canRestoreOriginalDestinationStack()` false.

Use TDD: tests first, verify inventory classes are missing, then implement the minimum pure routing/state machine and run full CI.
