scoreboard players remove @s md_timer 1
execute if score @s md_timer matches 80 run particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 1 1.5 1 0.04 30 force
execute if score @s md_timer matches 40 run playsound minecraft:block.stone.break master @a[distance=..48] ~ ~ ~ 1.1 0.7
execute if score @s md_timer matches 0 run function medusa:boss/transition/finish
