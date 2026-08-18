scoreboard players add $next_eid md_eid 1
scoreboard players operation @s md_eid = $next_eid md_eid
scoreboard players set @s md_state 0
scoreboard players set @s md_phase 0
scoreboard players set @s md_timer 0
scoreboard players set @s md_p1_done 0
scoreboard players set @s md_p2_done 0
scoreboard players set @s md_p3_done 0
scoreboard players set @s md_dungeon_clear 0
scoreboard players set @s md_rewarded 0
tag @s remove md.new_instance
function medusa:dungeon/build_generated
execute store result storage medusa:macro ctx.eid int 1 run scoreboard players get @s md_eid
function medusa:instance/participants/register_initial with storage medusa:macro ctx
