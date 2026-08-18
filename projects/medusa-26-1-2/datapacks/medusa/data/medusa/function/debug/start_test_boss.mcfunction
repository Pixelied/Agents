execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run scoreboard players set @s md_dungeon_clear 1
execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run scoreboard players set @s md_state 2
execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run scoreboard players set @s md_phase 1
execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run function medusa:arena/seal
execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run function medusa:boss/bootstrap
