execute if score $maze_debug_aborted md_tmp matches 1 run say MEDUSA_MAZE_OCCUPIED_ABORT_OK
execute unless score $maze_debug_aborted md_tmp matches 1 run say MEDUSA_MAZE_OCCUPIED_ABORT_FAILED
kill @e[type=minecraft:armor_stand,tag=md.maze.occupancy_probe]
function medusa:maze/transition/commit
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:debug/maze_smoke/mark_recovery_edge_ctx with storage medusa:macro maze
execute as @e[type=minecraft:marker,tag=md.debug_recovery_edge,limit=1] at @s run fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:barrier
summon minecraft:block_display ~ ~1 ~ {Tags:["md.maze.wall_display","md.debug_recovery_display"],block_state:{Name:"minecraft:stone_bricks"}}
scoreboard players operation @e[type=minecraft:block_display,tag=md.debug_recovery_display,limit=1,sort=nearest] md_eid = @s md_eid
summon minecraft:marker ~ ~1 ~ {Tags:["md.maze.wall_controller","md.debug_recovery_controller"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.debug_recovery_controller,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players set @s md_mphase 6
function medusa:maze/recovery/recover_one
function medusa:debug/maze_smoke/check_recovery
