scoreboard players set @s md_attack 6
scoreboard players set @s md_atk_timer 0
execute store result storage medusa:macro serpent.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/attack/large_serpent/spawn_target with storage medusa:macro serpent
playsound minecraft:entity.ender_dragon.flap hostile @a[distance=..48] ~ ~ ~ 1.0 0.55
