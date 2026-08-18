function medusa:gaze/tick_for_boss
execute if score @s md_timer matches 1.. run function medusa:boss/transition/tick
execute unless score @s md_timer matches 1.. if score @s md_phase matches 1 if score @s md_hp <= @s md_p2hp run function medusa:boss/transition/start_phase2
execute unless score @s md_timer matches 1.. if score @s md_phase matches 2 if score @s md_hp <= @s md_p3hp run function medusa:boss/transition/start_phase3
execute unless score @s md_timer matches 1.. run function medusa:boss/director/cooldowns
execute unless score @s md_timer matches 1.. if score @s md_attack matches 1.. run function medusa:boss/director/tick_attack
execute unless score @s md_timer matches 1.. if score @s md_attack matches 0 if score @s md_cd matches 0 run function medusa:boss/director/select
execute store result storage medusa:macro boss.eid int 1 run scoreboard players get @s md_eid
execute store result storage medusa:macro boss.hp int 1 run scoreboard players get @s md_hp
execute store result storage medusa:macro boss.maxhp int 1 run scoreboard players get @s md_maxhp
function medusa:boss/bossbar/update with storage medusa:macro boss
