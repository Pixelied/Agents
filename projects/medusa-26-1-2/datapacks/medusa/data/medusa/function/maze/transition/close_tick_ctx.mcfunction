$execute as @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=2}] at @s run function medusa:maze/wall/close_tick
$execute as @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=3}] at @s run function medusa:maze/wall/abort_tick
$execute unless entity @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=2..3}] run scoreboard players set @s md_mphase 8
