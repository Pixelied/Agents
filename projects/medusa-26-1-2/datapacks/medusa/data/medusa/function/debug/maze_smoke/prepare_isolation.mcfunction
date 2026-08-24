kill @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell]
summon minecraft:marker ~160 ~-18 ~30 {Tags:["md.maze.cell","md.debug_maze_secondary_cell"]}
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_eid 900001
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_mrow 0
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_mcol 0
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_me 1
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_ne 1
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_ms 1
scoreboard players set @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,limit=1,sort=nearest] md_ns 1
