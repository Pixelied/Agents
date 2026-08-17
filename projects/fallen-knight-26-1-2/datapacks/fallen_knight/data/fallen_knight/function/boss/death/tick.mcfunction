scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1 run playsound minecraft:entity.wither.death hostile @a[distance=..28] ~ ~ ~ 0.35 1.45
execute if score @s fk_timer matches 1..14 run particle minecraft:smoke ~ ~1 ~ 0.25 0.45 0.25 0.02 3 force @a[distance=..28]
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
data modify storage fallen_knight:macro arena.hp set value 0
function fallen_knight:arena/bossbar/update with storage fallen_knight:macro arena
execute if score @s fk_timer matches 15.. run function fallen_knight:boss/death/finish
