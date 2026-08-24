scoreboard players set @s md_manim 0
tag @s add md.maze.mutate_source
execute if score @s md_mparent matches 3 run scoreboard players set @s md_manim 1
$execute if score @s md_mrow matches ..11 positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] if score @s md_mparent matches 1 run scoreboard players set @e[type=minecraft:marker,tag=md.maze.mutate_source,scores={md_eid=$(eid)},distance=..8,limit=1] md_manim 1
execute if score @s md_mrow matches ..11 if score @s md_mseen matches 0 if score @s md_ns matches 1 if score @s md_manim matches 0 run function medusa:maze/propose/do_close_south with storage medusa:macro maze
tag @s remove md.maze.mutate_source
