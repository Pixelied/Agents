scoreboard players operation @s md_nparent = @e[type=minecraft:marker,tag=md.maze.spread_source,distance=..8,limit=1,sort=nearest] md_roll
scoreboard players set @s md_mseen 1
scoreboard players set @s md_mfront 2
scoreboard players operation @s md_mdist = @e[type=minecraft:marker,tag=md.maze.spread_source,distance=..8,limit=1,sort=nearest] md_mdist
scoreboard players add @s md_mdist 1
