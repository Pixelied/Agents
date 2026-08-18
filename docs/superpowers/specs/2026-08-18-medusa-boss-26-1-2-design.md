# Medusa Boss Design — Minecraft Java 26.1.2

**Status:** Approved design, ready for human spec review  
**Target:** Minecraft Java 26.1.2  
**Gameplay layer:** Datapack-first, multiplayer-safe  
**Visual layer:** Swappable resource pack; ETF/EMF may be used if useful for entity presentation, but gameplay must remain correct without the visual layer

## 1. Design goals

Medusa is a full mini-dungeon encounter rather than a boss placed in a room. Players enter a ruined surface temple, descend into an underground Gorgon labyrinth, solve a small number of simple themed puzzles, survive snakes and traps, and finally enter a purpose-built arena where Medusa is released from a giant stone prison.

The encounter must:

- feel specifically like Medusa rather than a generic fantasy boss;
- make gaze management and petrification the signature mechanic;
- remain readable and fair in solo and multiplayer play;
- support multiple generated temples and multiple simultaneous encounters on one server;
- avoid long cutscenes, procedural-maze frustration, or complicated puzzle logic;
- provide a repeatable reward loop centered on Gorgon materials and the Medusa Staff;
- separate gameplay identifiers and state from visual assets so models/textures can be replaced later without rewriting mechanics.

## 2. Encounter flow

The fixed progression is:

**Surface Temple → Underground Descent → Labyrinth → Inner Lair → Medusa Arena**

The main route is handcrafted and fixed. Optional branches provide treasure, traps, snake encounters, petrified victims, environmental storytelling, shortcuts, and dead ends, but required progression never depends on randomly guessing the correct corridor.

Timing targets:

- experienced critical path: about 10 minutes;
- first-time clear with exploration: roughly 15–25 minutes;
- repeat Medusa summons do not require re-solving the full dungeon after the temple has been cleared once.

After a temple's first clear, required puzzle doors stay unlocked for that temple instance. Ambient snakes and traps may repopulate after the dungeon has been empty for a while, but one-time side loot does not automatically refill.

## 3. Required puzzle set

The dungeon contains three required puzzles. Each should be understandable from the room itself within seconds and should use obvious feedback. Failure causes a short trap, snake ambush, or retry, never a full-dungeon reset.

### 3.1 Averted Eyes

Three stone warriors face a central Gorgon idol. Players use nearby controls to rotate the warriors until none is looking directly at the idol. When all three are correctly turned away, the exit opens. A wrong submit causes the idol's eyes to flash and releases a small snake ambush before the room can be retried.

### 3.2 Borrowed Gaze

A simple line-of-sight room contains a Gorgon eye emitter, two movable/rotatable mirror-style pedestals, and a petrified door seal. Players redirect a clearly visible particle/light beam into the seal. The room has only a few valid pedestal positions and one obvious final alignment; it is not a freeform optics puzzle.

### 3.3 The Blind Passage

A short corridor teaches cover and line-of-sight. Wall eyes activate in a visible sequence while broken pillars create safe blind spots. Players cross by moving between cover windows. Being caught triggers Slowness/stone effects and a short snake/trap response, then the sequence resets. This teaches the boss's core gaze rule before the arena.

## 4. Arena, Golden Gorgon Eye, and awakening

The final arena is a large circular chamber with a strong central focal point and enough open space for movement. Broken columns, petrified victims, and pieces of the central prison provide line-of-sight cover during early combat.

At the center is an enormous stone statue shaped like Medusa. It is not merely a sculpture: it is Medusa herself, sealed inside a stone prison.

In front of the statue is the **Golden Gorgon Eye**, presented as a custom player head. The approved texture value is:

```text
eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ1NzdkOWU1YTVhZGM4ZTA5MzYyOTVlYjYzMDBmZGUwZmY5YjAyM2YyMGJlZmMxNTNiMjhkZWVlYTgwNDdhMSJ9fX0=
```

The production item is named **Golden Gorgon Eye** and must not retain minecraft-heads.com marketing lore. Suggested lore:

> An ancient seal containing a power best left undisturbed.

On the first encounter, taking the Eye from its pedestal starts the awakening:

1. the arena exits seal;
2. the pedestal sinks/locks;
3. cracking sounds and stone particles spread across the central statue;
4. chunks of the prison break away and remain as early-fight cover;
5. Medusa emerges from inside the statue;
6. her eyes open, the boss bar appears, and combat begins.

The reveal should be short and interactive-friendly, not a long cutscene.

## 5. Medusa visual direction

The approved visual direction is a hybrid Gorgon:

- humanoid upper body;
- large serpent lower body instead of legs;
- living snake hair;
- clawed hands;
- bright supernatural eyes;
- cracked stone still fused to parts of her body from centuries of petrification;
- stone pieces progressively breaking away at phase transitions.

The selected base texture is the user's first dark teal/green Medusa skin candidate; the third candidate was an identical duplicate. That skin is the under-layer for the face, torso, arms, clothing, and palette. The boss presentation adds the serpent body, 3D snake hair, claws, glowing gaze eyes, and stone-shell geometry around it.

The central prison statue should use the same overall silhouette so players initially read it as an ancient statue and only realize during the awakening that it is the actual boss.

If the resource pack or optional visual helpers are missing, the fallback boss entity, particles, sounds, boss bar, hitbox, attacks, gaze system, and progression must still work correctly.

## 6. Arena and encounter state machine

Each generated temple owns an independent encounter instance. The implementation must never use one global Medusa state for the entire server.

Per-instance states:

1. `sealed` — first-clear Eye is still on the pedestal;
2. `awakening` — Eye has been taken or a rematch ritual has completed and the reveal is running;
3. `active` — Medusa is alive;
4. `defeated` — death sequence and rewards are being resolved;
5. `ritual_ready` — Medusa is dead and the pedestal accepts the Eye plus a rematch offering;
6. `resetting` — temporary cleanup/failsafe state after a wipe, unload, or interrupted transition.

Every instance uses its own anchor/identifier so selectors, summons, puzzle state, petrification logic, rewards, and cleanup cannot leak into another temple.

Initial combat tuning target is **300 HP solo + 75 HP per additional active participant, capped at 600 HP**. Damage values and cooldowns remain normal-boss difficulty and do not multiply with player count. These are starting balance values to be playtested, not a reason to change the encounter's mechanics.

## 7. Core gaze and petrification system

Petrification is tracked per player from 0–100.

A player is considered to be looking at Medusa only when:

- Medusa's head/eye point is inside a roughly 25-degree view cone from the player's look direction; and
- an unobstructed line of sight exists between the player's eyes and Medusa's eye/head point.

Columns, statues, prison debris, walls, and other solid cover therefore break the gaze.

### 7.1 Normal gaze

Normal eye contact builds Petrification at an initial target rate of **12 points per second**. Looking away does not instantly erase it: after a **0.5-second delay**, Petrification drains at **20 points per second** until zero.

This lets players risk short glances to aim and attack, while repeated glances still accumulate danger.

### 7.2 Petrification thresholds

- **0–39:** action-bar meter plus subtle stone particles;
- **40–69:** noticeable movement slowdown and more cracking/stone feedback;
- **70–89:** severe movement penalty, stronger cracking sounds, obvious visual warning;
- **90–99:** near-total stiffness and urgent warning feedback;
- **100:** full petrification.

The action bar should remain compact, for example `Petrification: 72%`, rather than covering the screen with large titles.

### 7.3 Gorgon Gaze

Gorgon Gaze is Medusa's signature burst attack.

Telegraph:

- eyes flare brightly;
- snakes hiss sharply;
- a unique charge sound plays;
- the action bar briefly warns **LOOK AWAY!**;
- telegraph lasts about **1.75 seconds**.

The empowered gaze then remains active for about **2.5 seconds**. During it, eye contact builds Petrification at an initial target rate of **55 points per second**. The player can completely avoid the burst by turning away or breaking line of sight with arena cover.

The telegraph must be obvious enough that failure feels like a reaction/positioning mistake, not an invisible attack.

## 8. Full petrification and multiplayer rescue

At 100 Petrification, the victim becomes a stone statue:

- movement, jumping, attacking, item use, and normal camera interaction are disabled as far as datapack mechanics allow;
- the victim receives strong stone/cracking feedback and a visible statue shell;
- after a 1-second grace period, the victim takes **2 health points (1 heart) of suffocation-style damage per second**, producing about a 10-second rescue window for a full-health 20-HP player.

Teammates can break the stone shell. Rescue progress uses 12 points total:

- direct pickaxe hit: +4;
- direct melee weapon hit: +2;
- bare-hand/other direct melee hit: +1;
- projectiles do not count.

Visual shell stages are:

- 0–3 progress: **Stone**;
- 4–7: **Cracked**;
- 8–11: **Shattered/critical**;
- 12: shell breaks and the player is freed.

Every valid rescue hit produces stone particles and a cracking sound. A rescued player returns at low Petrification and receives about **2 seconds of gaze grace** so Medusa cannot instantly repetrify them during the rescue animation.

On player death, disconnect, instance reset, or server recovery, all immobilization and statue state must be cleared safely.

## 9. Medusa combat phases

Medusa has three phases.

### Phase 1 — The Awakened Gorgon

**100%–60% HP**

- normal gaze and Gorgon Gaze;
- claw/melee attacks;
- Serpent Lash from her snake hair;
- limited mobility pressure;
- most arena cover remains intact.

This phase teaches the fight without being harmless.

### Phase 2 — Serpent Fury

**Below 60% HP**

Transition lasts about **5 seconds**. Medusa becomes briefly invulnerable, recoils/screams, snakes flare, and selected fragile cover cracks or collapses.

New pressure:

- faster attack cadence;
- Venom Spit against distant players;
- stronger positioning pressure around Gorgon Gaze;
- occasional Brood Call;
- less safe cover.

### Phase 3 — Wrath of Medusa

**Below 28% HP**

A second roughly **5-second** invulnerable transition destroys most remaining fragile cover and visually removes more stone from Medusa's body.

Phase 3 adds:

- more aggressive attack chaining;
- fewer safe sightline positions;
- slightly more frequent Gorgon Gaze opportunities;
- occasional Large Serpent Strike;
- no uncontrolled mob spam.

Phase-transition flags are one-shot per encounter and must survive unusual damage timing without retriggering.

## 10. Snake and venom attacks

The attack set stays intentionally small so gaze management remains the star mechanic.

### Serpent Lash

Close-range snake-hair strike. Fast, readable, modest knockback, and a short poison effect.

### Venom Spit

Used against distant or stationary players. A visible projectile lands and leaves a temporary venom patch. The patch is short-lived and clearly telegraphed; it must not permanently alter terrain.

### Brood Call

Occasionally summons **2–4 fragile, fast snakes**. They exist to distract and pressure players rather than become a second boss. They are easy to kill and are cleaned up when the encounter ends.

### Large Serpent Strike

Phase-3-only spectacle attack. A much larger serpent manifestation erupts or sweeps through a clearly telegraphed section of the arena, performs one dangerous attack, then disappears. It is an ability, not a permanent additional boss mob.

## 11. Failure, joining, leaving, and cleanup

Multiplayer behavior is server-authoritative and instance-local.

- Late joiners who enter an active arena become participants in that instance.
- Dead, disconnected, or departed players have personal petrification/channel state cleared.
- If every living participant is dead or outside the encounter for a short timeout, the fight resets to a valid retry/rematch state.
- Temporary snakes, venom zones, display entities, particles/controllers, staff channel markers, and statue shells are removed on cleanup.
- Phase transitions can trigger only once per spawn.
- Reward resolution has a one-shot guard so unusual death timing cannot duplicate loot.
- A server restart during `awakening`, `active`, or `defeated` must recover to a valid state instead of leaving doors, the Eye, or Medusa permanently stuck.

### Golden Gorgon Eye failsafe

The Eye is a persistent encounter key. The instance records whether its canonical Eye is on the pedestal, carried/dropped in the world, or temporarily locked into an active ritual. If the Eye is genuinely missing after a reset or restart, the encounter reconstructs the canonical Eye on the pedestal. Ritual logic ignores accidental extra tagged copies rather than allowing duplicate encounter states.

Any future cross-boss crafting use of the Golden Gorgon Eye must preserve the ability to perform Medusa rematches; the Medusa Staff recipe does not consume it.

## 12. Medusa rewards

Medusa drops one shared encounter loot bundle per kill. Initial tuning target:

- **1 Medusa's Heart** — guaranteed signature component;
- **10–14 Gorgon Scales** — guaranteed;
- **2 Serpent Fangs** — guaranteed;
- **2–4 Diamonds**;
- **8–16 Gold Ingots**;
- **25% chance for 1 Netherite Scrap**;
- approximately **100 XP** worth of boss reward.

Exact vanilla-material counts may be adjusted during playtesting, but the guaranteed Heart, renewable Scales/Fangs, and no routine full-Netherite-Ingot drop are part of the design.

## 13. Medusa Staff

The main crafted reward is the **Medusa Staff**.

Recipe ingredients:

- 1 Medusa's Heart;
- 4 Gorgon Scales;
- 1 Serpent Fang;
- 1 Netherite Ingot;
- 1 Breeze Rod.

The exact 3×3 slot arrangement is an implementation/UI detail; the ingredient set is fixed.

The staff has **64 Gorgon Charges** maximum. It does not permanently break at zero; it becomes dormant until recharged.

### 13.1 Recharging

One Gorgon Scale restores **8 charges**, up to the 64-charge cap.

Preferred interaction: Medusa Staff in the main hand, Gorgon Scale in the offhand, sneak + use. The scale is consumed only when the staff is below 64 charges. Recharge feedback shows the new charge count.

### 13.2 Quick petrify

A quick use on a valid target costs **1 charge** and applies a strong temporary partial petrification: heavy Slowness, Weakness, stone particles, and cracking feedback without fully disabling the target.

Initial range target: about **16 blocks** with clear line of sight.

### 13.3 Channeled petrification

Holding use channels the staff onto the aimed target. The caster is heavily slowed and cannot sprint while channeling.

Counterplay is mandatory:

- breaking line of sight interrupts the channel;
- taking damage interrupts the caster's channel;
- moving the crosshair off the target breaks the lock after a very short tolerance;
- partial petrification decays gradually after interruption rather than disappearing instantly.

The staff consumes **1 charge per completed second of channeling**.

Channel progression:

- about 1 second: strong slowdown;
- about 2 seconds: near immobilization;
- about 3 seconds: full petrification;
- about 5 seconds of uninterrupted total channeling: suffocation/crushing damage begins while the beam is maintained.

Normal mobs and players remain fully petrified for about **5 seconds** after the beam stops, then automatically crack free. Continuing to channel after the full-petrification point is what turns the move from control into damage.

Boss-class targets use the same buildup so the staff feels consistent, but automatically crack free after about **1.5 seconds** of full petrification and are immune to the staff's suffocation finisher. This prevents the staff from deleting other boss mechanics while still rewarding a successful channel.

### 13.4 Stone Spikes

Sneak + use on targeted ground, when the offhand is not being used to recharge, casts **Stone Spikes** for **4 charges**.

Initial behavior:

- target ground within roughly 12 blocks;
- erupt a compact cluster/line of jagged stone spikes;
- deal a strong but non-one-shot burst (initial target about 6–8 damage before normal mitigation where applicable);
- knock affected enemies upward/backward;
- use temporary visual/interaction entities or reversible effects rather than permanently editing terrain.

A full 64-charge staff therefore supports at most 16 Stone Spikes casts if no charges are spent on petrification.

## 14. Rematch ritual

After Medusa is defeated, that temple enters `ritual_ready`.

To summon Medusa again:

1. place the **Golden Gorgon Eye** back on the original pedestal;
2. provide **4 Gorgon Scales + 1 Serpent Fang** as the offering;
3. the Eye locks into the seal but is not consumed;
4. the offering is consumed once the summon successfully commits;
5. the stone prison reforms and Medusa awakens again;
6. after Medusa is defeated, the Eye becomes collectible again.

If the ritual is interrupted before Medusa actually spawns, its offering is not lost. If the boss fully spawns and the party later wipes, the offering remains spent.

Required puzzle doors stay open after first clear so rematches do not require repeating the full labyrinth. Ambient traps/snakes may reset after roughly 15 minutes with no players inside the dungeon.

## 15. Technical boundaries

The implementation should keep the following systems independent rather than building one giant tick function:

1. **Dungeon instance controller** — generated-instance identity, first-clear state, puzzle-door permanence, ambient reset;
2. **Puzzle controllers** — one small controller per puzzle;
3. **Arena controller** — doors, pedestal, Eye, state machine, awakening, wipe/reset logic;
4. **Medusa controller** — health scaling, phase flags, attack scheduler, movement/targeting;
5. **Gaze/petrification system** — per-player look checks, meter, threshold effects, full statue state;
6. **Rescue system** — shell stages, valid rescue hits, breakout and grace period;
7. **Snake/venom system** — temporary summons and hazard cleanup;
8. **Rewards/items** — boss loot, canonical custom item identifiers, recipes/recharge materials;
9. **Medusa Staff system** — tap/hold interpretation, target lock, charge accounting, Stone Spikes;
10. **Failsafe/recovery** — restart recovery, orphan cleanup, Eye restoration, duplicate-reward guards.

Instance-local state should use stable anchor entities and/or namespaced storage plus scores/tags. No selector should accidentally operate on every Medusa temple in the dimension.

High-frequency gaze/staff checks run only for active instances and relevant nearby players. Inactive dungeons should have negligible tick cost.

## 16. Visual asset boundaries

Gameplay IDs must not depend on the filename or source of any downloaded texture/model.

Visual layer responsibilities include:

- Medusa custom appearance and animation pieces;
- serpent tail and snake hair;
- cracked-stone phase overlays;
- central prison statue;
- statue-shell presentation for petrified players;
- optional custom visuals for Medusa's Heart, Gorgon Scales, Serpent Fangs, Medusa Staff, venom, and Stone Spikes;
- Golden Gorgon Eye presentation using the approved head texture.

Gameplay layer responsibilities include all hitboxes, timers, state changes, damage, line-of-sight checks, drops, recipes, charges, ritual logic, cleanup, and progression.

ETF/EMF may be included if the selected entity-model approach benefits from them, but no boss mechanic may require a client visual mod to function.

## 17. Verification strategy

Before implementation is considered complete, verify at minimum:

- first-clear dungeon progression and all three puzzles;
- 10-minute experienced critical path target is plausible;
- solo Medusa fight through all phases;
- 2-player and larger-party fights;
- two separate Medusa temples active simultaneously;
- Gorgon Gaze with direct sight, turning away, and physical cover;
- normal Petrification buildup/decay at every threshold;
- rescue at each shell stage and at the last possible moment;
- death while petrified;
- disconnect/reconnect while petrified;
- party wipe in each phase and during transitions;
- server restart during `awakening`, `active`, `defeated`, and `ritual_ready`;
- canonical Golden Gorgon Eye recovery if dropped or lost during reset;
- one-shot reward guard;
- rematch ritual success, interruption, wipe, and subsequent reuse;
- staff quick petrify, interrupted channels, 3-second full petrification, post-5-second suffocation behavior, boss resistance, and PvP counterplay;
- exact 64-charge cap, 1-charge-per-second channel drain, 4-charge Stone Spikes, and 8-charge-per-scale recharge;
- resource-pack-disabled fallback behavior;
- cleanup of snakes, venom, statue shells, displays, and channel helpers;
- acceptable server tick cost with inactive versus active temple instances.

## 18. Non-goals

This design intentionally does not include:

- a procedural/randomized labyrinth;
- complicated ciphers or long puzzle chains;
- giant snake-mob swarms;
- permanent Stone Spikes terrain griefing;
- an infinite-charge Medusa Staff;
- mandatory client mods for gameplay correctness;
- a generic reusable spell framework beyond what the Medusa encounter and staff need;
- a finalized cross-boss artifact recipe involving the Golden Gorgon Eye. That remains a future boss-system decision.
