clear @s minecraft:diamond 1
clear @s minecraft:soul_sand 4
clear @s minecraft:iron_ingot 4
execute at @s as @e[type=minecraft:marker,tag=fk.arena,scores={fk_state=2},sort=nearest,limit=1,distance=..4] at @s run function fallen_knight:arena/rematch_spawn
playsound minecraft:block.beacon.activate master @s ~ ~ ~ 0.8 0.65
