scoreboard players set @s md_phase 2
scoreboard players set @s md_timer 100
scoreboard players set @s md_attack 0
scoreboard players set @s md_atk_timer 0
scoreboard players set @s md_gorgon_active 0
data merge entity @s {Invulnerable:1b}
execute store result storage medusa:macro boss.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/transition/phase2_arena with storage medusa:macro boss
playsound minecraft:entity.husk.ambient master @a[distance=..48] ~ ~ ~ 1.4 0.6
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 1.5 2 1.5 0.05 60 force
