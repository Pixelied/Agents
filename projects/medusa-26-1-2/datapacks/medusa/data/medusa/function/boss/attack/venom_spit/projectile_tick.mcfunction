scoreboard players add @s md_timer 1
particle minecraft:dust{color:[0.2f,0.8f,0.15f],scale:0.8f} ~ ~ ~ 0.08 0.08 0.08 0.01 3 force
execute store result storage medusa:macro venom.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/attack/venom_spit/projectile_step with storage medusa:macro venom
execute if score @s md_timer matches 61.. run kill @s
