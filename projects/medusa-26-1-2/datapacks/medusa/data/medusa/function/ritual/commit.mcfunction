scoreboard players set @s md_eye_state 2
scoreboard players set @s md_rewarded 0
scoreboard players set @s md_phase 0
scoreboard players set @s md_reset 0
execute store result storage medusa:macro reset.eid int 1 run scoreboard players get @s md_eid
function medusa:arena/reset/cleanup_scoped with storage medusa:macro reset
function medusa:dungeon/restore_cover
function medusa:arena/awakening/start
