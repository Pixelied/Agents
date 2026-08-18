scoreboard players set @s md_phase 3
scoreboard players set @s md_timer 100
data merge entity @s {Invulnerable:1b}
execute store result storage medusa:macro boss.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/transition/phase3_arena with storage medusa:macro boss
playsound minecraft:entity.ender_dragon.growl master @a[distance=..48] ~ ~ ~ 0.8 1.1
particle minecraft:explosion ~ ~1 ~ 1.5 2 1.5 0 5 force
