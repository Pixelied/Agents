scoreboard players add $next_eid md_eid 1
scoreboard players operation @s md_eid = $next_eid md_eid
scoreboard players set @s md_state 0
scoreboard players set @s md_phase 0
scoreboard players set @s md_timer 0
scoreboard players set @s md_dungeon_clear 0
scoreboard players set @s md_rewarded 0
scoreboard players set @s md_ritual_paid 0
scoreboard players set @s md_mphase 0
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mgen_try 0
scoreboard players set @s md_mdelta 0
tag @s remove md.new_instance
function medusa:dungeon/build_generated
function medusa:maze/setup/start
function medusa:arena/pedestal/spawn_eye
execute store result storage medusa:macro ctx.eid int 1 run scoreboard players get @s md_eid
function medusa:instance/participants/register_initial with storage medusa:macro ctx
