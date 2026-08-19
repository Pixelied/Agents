# Exact-runtime lifecycle / recovery / instance-isolation smoke. Runs as the primary instance marker.
execute store result storage medusa:macro lifecycle.eid int 1 run scoreboard players get @s md_eid

# Phase transition 1 -> 2.
execute as @e[type=minecraft:husk,tag=md.boss,distance=..110,limit=1,sort=nearest] at @s run function medusa:boss/transition/start_phase2
execute if score @s md_phase matches 2 if entity @e[type=minecraft:husk,tag=md.boss,scores={md_phase=2},distance=..110,limit=1] run say MEDUSA_PHASE2_OK
execute unless score @s md_phase matches 2 run say MEDUSA_PHASE2_FAILED
execute if score @s md_phase matches 2 unless entity @e[type=minecraft:husk,tag=md.boss,scores={md_phase=2},distance=..110,limit=1] run say MEDUSA_PHASE2_FAILED

# Phase transition 2 -> 3.
execute as @e[type=minecraft:husk,tag=md.boss,distance=..110,limit=1,sort=nearest] at @s run function medusa:boss/transition/start_phase3
execute if score @s md_phase matches 3 if entity @e[type=minecraft:husk,tag=md.boss,scores={md_phase=3},distance=..110,limit=1] run say MEDUSA_PHASE3_OK
execute unless score @s md_phase matches 3 run say MEDUSA_PHASE3_FAILED
execute if score @s md_phase matches 3 unless entity @e[type=minecraft:husk,tag=md.boss,scores={md_phase=3},distance=..110,limit=1] run say MEDUSA_PHASE3_FAILED
execute as @e[type=minecraft:husk,tag=md.boss,distance=..110,limit=1,sort=nearest] run function medusa:boss/transition/finish

# Runtime isolation probe: cleanup for one fake encounter must not touch another fake encounter.
bossbar add medusa:arena_999998 {"text":"Medusa isolation probe"}
summon minecraft:husk ~4 ~ ~ {Tags:["md.boss","md.iso_primary"],PersistenceRequired:1b,Silent:1b}
scoreboard players set @e[type=minecraft:husk,tag=md.iso_primary,distance=..8,limit=1] md_eid 999998
summon minecraft:silverfish ~5 ~ ~ {Tags:["md.snake","md.iso_primary"],PersistenceRequired:1b,Silent:1b}
scoreboard players set @e[type=minecraft:silverfish,tag=md.iso_primary,distance=..8,limit=1] md_eid 999998
summon minecraft:block_display ~6 ~ ~ {Tags:["md.statue_shell","md.iso_primary"],block_state:{Name:"minecraft:stone"}}
scoreboard players set @e[type=minecraft:block_display,tag=md.iso_primary,distance=..8,limit=1] md_eid 999998
summon minecraft:interaction ~7 ~ ~ {Tags:["md.statue_hitbox","md.iso_primary"],width:1.0f,height:1.9f,response:1b}
scoreboard players set @e[type=minecraft:interaction,tag=md.iso_primary,distance=..8,limit=1] md_eid 999998
summon minecraft:husk ~4 ~ ~2 {Tags:["md.boss","md.iso_other"],PersistenceRequired:1b,Silent:1b}
scoreboard players set @e[type=minecraft:husk,tag=md.iso_other,distance=..8,limit=1] md_eid 999999
summon minecraft:silverfish ~5 ~ ~2 {Tags:["md.snake","md.iso_other"],PersistenceRequired:1b,Silent:1b}
scoreboard players set @e[type=minecraft:silverfish,tag=md.iso_other,distance=..8,limit=1] md_eid 999999
summon minecraft:block_display ~6 ~ ~2 {Tags:["md.statue_shell","md.iso_other"],block_state:{Name:"minecraft:stone"}}
scoreboard players set @e[type=minecraft:block_display,tag=md.iso_other,distance=..8,limit=1] md_eid 999999
summon minecraft:interaction ~7 ~ ~2 {Tags:["md.statue_hitbox","md.iso_other"],width:1.0f,height:1.9f,response:1b}
scoreboard players set @e[type=minecraft:interaction,tag=md.iso_other,distance=..8,limit=1] md_eid 999999
data modify storage medusa:macro isolation.eid set value 999998
function medusa:arena/reset/cleanup_scoped with storage medusa:macro isolation
execute unless entity @e[tag=md.iso_primary,distance=..12] if entity @e[type=minecraft:husk,tag=md.iso_other,distance=..12] if entity @e[type=minecraft:silverfish,tag=md.iso_other,distance=..12] if entity @e[type=minecraft:block_display,tag=md.iso_other,distance=..12] if entity @e[type=minecraft:interaction,tag=md.iso_other,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_OK
execute if entity @e[tag=md.iso_primary,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_FAILED
execute unless entity @e[type=minecraft:husk,tag=md.iso_other,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_FAILED
execute unless entity @e[type=minecraft:silverfish,tag=md.iso_other,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_FAILED
execute unless entity @e[type=minecraft:block_display,tag=md.iso_other,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_FAILED
execute unless entity @e[type=minecraft:interaction,tag=md.iso_other,distance=..12] run say MEDUSA_INSTANCE_ISOLATION_FAILED
kill @e[tag=md.iso_other,distance=..12]

# Death must atomically resolve reward state, remove the boss, return the Eye, and enter ritual_ready.
execute as @e[type=minecraft:husk,tag=md.boss,distance=..110,limit=1,sort=nearest] at @s run function medusa:boss/death/start
execute if score @s md_state matches 4 if score @s md_rewarded matches 1 if score @s md_killed matches 1 unless entity @e[type=minecraft:husk,tag=md.boss,distance=..110] if entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] run say MEDUSA_DEATH_STATE_OK
execute unless score @s md_state matches 4 run say MEDUSA_DEATH_STATE_FAILED
execute unless score @s md_rewarded matches 1 run say MEDUSA_DEATH_STATE_FAILED
execute unless score @s md_killed matches 1 run say MEDUSA_DEATH_STATE_FAILED
execute if entity @e[type=minecraft:husk,tag=md.boss,distance=..110] run say MEDUSA_DEATH_STATE_FAILED
execute unless entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] run say MEDUSA_DEATH_STATE_FAILED

# Reward one-shot guard: a second distribution call must create no new item entities.
execute positioned ~64 ~-16 ~72 run tag @e[type=minecraft:item,distance=..16] add md.reward_before
function medusa:reward/distribute
execute positioned ~64 ~-16 ~72 unless entity @e[type=minecraft:item,tag=!md.reward_before,distance=..16] run say MEDUSA_REWARD_GUARD_OK
execute positioned ~64 ~-16 ~72 if entity @e[type=minecraft:item,tag=!md.reward_before,distance=..16] run say MEDUSA_REWARD_GUARD_FAILED
tag @e[type=minecraft:item,tag=md.reward_before,distance=..110] remove md.reward_before

# Eye recovery: remove both pedestal helpers, then recover the canonical Eye from instance state.
function medusa:arena/pedestal/remove with storage medusa:macro lifecycle
scoreboard players set $eye_removed md_tmp 0
execute unless entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] unless entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..110] run scoreboard players set $eye_removed md_tmp 1
function medusa:instance/recover_one
execute if score $eye_removed md_tmp matches 1 if entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] if entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..110] run say MEDUSA_EYE_RECOVERY_OK
execute unless score $eye_removed md_tmp matches 1 run say MEDUSA_EYE_RECOVERY_FAILED
execute unless entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] run say MEDUSA_EYE_RECOVERY_FAILED
execute unless entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..110] run say MEDUSA_EYE_RECOVERY_FAILED

# Restart recovery: emulate an active rematch with the Eye removed; load recovery must reset to ritual_ready.
function medusa:arena/pedestal/remove with storage medusa:macro lifecycle
scoreboard players set @s md_eye_state 1
scoreboard players set @s md_state 2
function medusa:boss/bootstrap
function medusa:instance/recover_loaded
execute if score @s md_state matches 4 unless entity @e[type=minecraft:husk,tag=md.boss,distance=..110] if entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] if entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..110] run say MEDUSA_RESTART_RECOVERY_OK
execute unless score @s md_state matches 4 run say MEDUSA_RESTART_RECOVERY_FAILED
execute if entity @e[type=minecraft:husk,tag=md.boss,distance=..110] run say MEDUSA_RESTART_RECOVERY_FAILED
execute unless entity @e[type=minecraft:item_display,tag=md.pedestal_display,distance=..110] run say MEDUSA_RESTART_RECOVERY_FAILED
execute unless entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..110] run say MEDUSA_RESTART_RECOVERY_FAILED
