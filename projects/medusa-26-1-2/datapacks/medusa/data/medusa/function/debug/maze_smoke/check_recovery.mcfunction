scoreboard players set $maze_recovery_portal md_tmp 0
execute as @e[type=minecraft:marker,tag=md.debug_recovery_edge,limit=1] at @s if block ~3 ~1 ~0 minecraft:air run scoreboard players set $maze_recovery_portal md_tmp 1
execute if score @s md_mphase matches 2 if score $maze_recovery_portal md_tmp matches 1 unless entity @e[tag=md.debug_recovery_display] unless entity @e[tag=md.debug_recovery_controller] run say MEDUSA_MAZE_RECOVERY_OK
execute unless score @s md_mphase matches 2 run say MEDUSA_MAZE_RECOVERY_FAILED
execute unless score $maze_recovery_portal md_tmp matches 1 run say MEDUSA_MAZE_RECOVERY_FAILED
function medusa:debug/maze_smoke/start_completion_probe
