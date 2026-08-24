$execute as @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=1}] at @s run function medusa:maze/wall/open_tick
$execute unless entity @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=1}] run function medusa:maze/transition/start_close
