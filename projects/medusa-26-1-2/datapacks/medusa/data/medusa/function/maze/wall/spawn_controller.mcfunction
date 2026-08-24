summon minecraft:marker ~ ~ ~ {Tags:["md.maze.wall_controller","md.maze.new_wall_controller"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_mrow = @s md_mrow
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_mcol = @s md_mcol
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_morient = @s md_roll
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_mmode $(mode)
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_manim 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] md_mblocked 0
