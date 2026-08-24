# Quick pulse must remain isolated from Medusa's boss-gaze meter.
kill @e[tag=md.staff_runtime_probe]
summon minecraft:husk 24 200 16 {Tags:["md.staff_runtime_probe","md.staff_quick_probe"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}
scoreboard players set @e[type=minecraft:husk,tag=md.staff_quick_probe,limit=1] md_petr 0
execute positioned 24 200 16 run function medusa:staff/quick/hit
execute if score @e[type=minecraft:husk,tag=md.staff_quick_probe,limit=1] md_petr matches 0 run say MEDUSA_STAFF_QUICK_ISOLATION_OK
execute unless score @e[type=minecraft:husk,tag=md.staff_quick_probe,limit=1] md_petr matches 0 run say MEDUSA_STAFF_QUICK_ISOLATION_FAILED
kill @e[type=minecraft:husk,tag=md.staff_quick_probe]

# Full petrification must preserve pre-existing NoAI and restore only NoAI it owns.
summon minecraft:husk 26 200 16 {Tags:["md.staff_runtime_probe","md.staff_noai_preexisting"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}
scoreboard players set @e[type=minecraft:husk,tag=md.staff_noai_preexisting,limit=1] md_tid 900001
execute as @e[type=minecraft:husk,tag=md.staff_noai_preexisting,limit=1] at @s run function medusa:staff/channel/full_petrify
execute as @e[type=minecraft:husk,tag=md.staff_noai_preexisting,limit=1] at @s run function medusa:staff/channel/release_target
summon minecraft:husk 28 200 16 {Tags:["md.staff_runtime_probe","md.staff_noai_owned"],NoAI:0b,NoGravity:1b,PersistenceRequired:1b}
scoreboard players set @e[type=minecraft:husk,tag=md.staff_noai_owned,limit=1] md_tid 900002
execute as @e[type=minecraft:husk,tag=md.staff_noai_owned,limit=1] at @s run function medusa:staff/channel/full_petrify
execute as @e[type=minecraft:husk,tag=md.staff_noai_owned,limit=1] at @s run function medusa:staff/channel/release_target
execute if data entity @e[type=minecraft:husk,tag=md.staff_noai_preexisting,limit=1] {NoAI:1b} unless entity @e[type=minecraft:husk,tag=md.staff_noai_preexisting,tag=md.staff_noai_applied,limit=1] unless data entity @e[type=minecraft:husk,tag=md.staff_noai_owned,limit=1] {NoAI:1b} unless entity @e[type=minecraft:husk,tag=md.staff_noai_owned,tag=md.staff_noai_applied,limit=1] run say MEDUSA_STAFF_NOAI_COMPAT_OK
execute unless data entity @e[type=minecraft:husk,tag=md.staff_noai_preexisting,limit=1] {NoAI:1b} run say MEDUSA_STAFF_NOAI_COMPAT_FAILED
execute if entity @e[type=minecraft:husk,tag=md.staff_noai_preexisting,tag=md.staff_noai_applied,limit=1] run say MEDUSA_STAFF_NOAI_COMPAT_FAILED
execute if data entity @e[type=minecraft:husk,tag=md.staff_noai_owned,limit=1] {NoAI:1b} run say MEDUSA_STAFF_NOAI_COMPAT_FAILED
execute if entity @e[type=minecraft:husk,tag=md.staff_noai_owned,tag=md.staff_noai_applied,limit=1] run say MEDUSA_STAFF_NOAI_COMPAT_FAILED
kill @e[type=minecraft:husk,tag=md.staff_noai_preexisting]
kill @e[type=minecraft:husk,tag=md.staff_noai_owned]

# Boss targets may visually reach full stone, but use the 1.5-second release window and never take Staff crushing damage.
scoreboard players set @e[type=minecraft:husk,tag=md.boss,limit=1] md_tid 900003
execute store result score $staff_boss_hp_before md_tmp run data get entity @e[type=minecraft:husk,tag=md.boss,limit=1] Health 1
execute as @e[type=minecraft:husk,tag=md.boss,limit=1] at @s run function medusa:staff/channel/full_petrify
execute as @e[type=minecraft:husk,tag=md.boss,limit=1] at @s run function medusa:staff/channel/apply_progress_target {tick:100}
execute store result score $staff_boss_hp_after md_tmp run data get entity @e[type=minecraft:husk,tag=md.boss,limit=1] Health 1
execute as @e[type=minecraft:husk,tag=md.boss,limit=1] at @s run function medusa:staff/channel/release_target
execute if score @e[type=minecraft:husk,tag=md.boss,limit=1] md_staff_stone_limit matches 0 if score $staff_boss_hp_before md_tmp = $staff_boss_hp_after md_tmp run say MEDUSA_STAFF_BOSS_LIMIT_OK
execute unless score $staff_boss_hp_before md_tmp = $staff_boss_hp_after md_tmp run say MEDUSA_STAFF_BOSS_LIMIT_FAILED
execute unless score @e[type=minecraft:husk,tag=md.boss,limit=1] md_staff_stone_limit matches 0 run say MEDUSA_STAFF_BOSS_LIMIT_FAILED
kill @e[tag=md.staff_runtime_probe]
