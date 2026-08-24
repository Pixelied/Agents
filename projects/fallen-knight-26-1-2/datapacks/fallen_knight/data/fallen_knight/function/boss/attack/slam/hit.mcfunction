playsound minecraft:block.anvil.land hostile @a[distance=..28] ~ ~ ~ 0.8 0.8
particle minecraft:poof ~ ~0.15 ~ 2.2 0.1 2.2 0.08 24 force @a[distance=..28]
execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/attack/slam/hit_for_arena with storage fallen_knight:macro boss
