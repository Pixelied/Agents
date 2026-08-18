tag @s add md.death_started
scoreboard players set @s md_attack 0
scoreboard players set @s md_atk_timer 0
scoreboard players set @s md_gorgon_active 0
execute store result storage medusa:macro death.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/death/finish with storage medusa:macro death
kill @s
