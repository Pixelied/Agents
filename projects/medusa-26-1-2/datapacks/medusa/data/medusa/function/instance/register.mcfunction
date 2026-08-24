scoreboard players add $next_eid md_eid 1
scoreboard players operation @s md_eid = $next_eid md_eid
scoreboard players set @s md_state 0
scoreboard players set @s md_phase 0
scoreboard players set @s md_timer 0
scoreboard players set @s md_dungeon_clear 0
scoreboard players set @s md_rewarded 0
scoreboard players set @s md_ritual_paid 0
scoreboard players set @s md_build 1
scoreboard players set @s md_mphase 0
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mgen_try 0
scoreboard players set @s md_mdelta 0
tag @s remove md.new_instance
