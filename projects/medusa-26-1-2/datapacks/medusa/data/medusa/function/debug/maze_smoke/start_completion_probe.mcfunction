kill @e[type=minecraft:marker,tag=md.maze.completion_probe]
summon minecraft:marker ~40 ~-16 ~113 {Tags:["md.maze.completion_probe"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.completion_probe,limit=1,sort=nearest] md_eid = @s md_eid
function medusa:maze/completion/check
tp @e[type=minecraft:marker,tag=md.maze.completion_probe,limit=1] ~47 ~-16 ~113
function medusa:maze/completion/check
execute if score @s md_dungeon_clear matches 1 if score @s md_mphase matches 9 if entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,limit=1] run say MEDUSA_MAZE_COMPLETE_OK
execute unless score @s md_dungeon_clear matches 1 run say MEDUSA_MAZE_COMPLETE_FAILED
execute unless score @s md_mphase matches 9 run say MEDUSA_MAZE_COMPLETE_FAILED
kill @e[type=minecraft:marker,tag=md.maze.completion_probe]
kill @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell]
tag @e[type=minecraft:marker,tag=md.debug_recovery_edge] remove md.debug_recovery_edge
schedule function medusa:debug/continue_smoke 1t replace
