scoreboard players add @s md_timer 1
particle minecraft:spore_blossom_air ~ ~0.15 ~ 1.5 0.15 1.5 0.01 4 force
execute store result storage medusa:macro venom.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_timer matches 1 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 11 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 21 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 31 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 41 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 51 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 61 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 71 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 81 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 91 run function medusa:boss/attack/venom_spit/hazard_apply with storage medusa:macro venom
execute if score @s md_timer matches 100.. run kill @s
