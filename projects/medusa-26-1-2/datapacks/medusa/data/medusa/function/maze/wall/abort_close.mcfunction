tag @s add md.maze.abort_source
execute if entity @e[type=minecraft:armor_stand,tag=md.maze.occupancy_probe,distance=..6] run scoreboard players set $maze_debug_aborted md_tmp 1
# If an east closure is unsafe, keep md_ne/md_nw open in NEXT.
execute if score @s md_morient matches 1 at @s as @e[type=minecraft:marker,tag=md.maze.cell,distance=..1,limit=1] if score @s md_eid = @e[type=minecraft:marker,tag=md.maze.abort_source,limit=1] md_eid run scoreboard players set @s md_ne 1
execute if score @s md_morient matches 1 at @s positioned ~7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,distance=..1,limit=1] if score @s md_eid = @e[type=minecraft:marker,tag=md.maze.abort_source,limit=1] md_eid run scoreboard players set @s md_nw 1
# If a south closure is unsafe, keep md_ns/md_nn open in NEXT.
execute if score @s md_morient matches 2 at @s as @e[type=minecraft:marker,tag=md.maze.cell,distance=..1,limit=1] if score @s md_eid = @e[type=minecraft:marker,tag=md.maze.abort_source,limit=1] md_eid run scoreboard players set @s md_ns 1
execute if score @s md_morient matches 2 at @s positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,distance=..1,limit=1] if score @s md_eid = @e[type=minecraft:marker,tag=md.maze.abort_source,limit=1] md_eid run scoreboard players set @s md_nn 1
execute if score @s md_morient matches 1 run fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:air
execute if score @s md_morient matches 2 run fill ~-1 ~1 ~3 ~1 ~7 ~4 minecraft:air
scoreboard players set @s md_mmode 3
scoreboard players set @s md_manim 0
function medusa:maze/wall/animate_abort
tag @s remove md.maze.abort_source
