scoreboard players add @s md_timer 1
particle minecraft:spore_blossom_air ~ ~0.15 ~ 1.5 0.15 1.5 0.01 4 force
execute store result storage medusa:macro venom.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_timer matches 1,11,21,31,41,51,61,71,81,91 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 100.. run kill @s
