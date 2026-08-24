scoreboard players add @s md_ray 1
execute unless block ~ ~ ~ #medusa:gaze_passable unless block ~ ~ ~ minecraft:lava if block ~ ~1 ~ #medusa:gaze_passable run function medusa:staff/spikes/mark_ground
execute if score @s md_staff_hit matches 0 if block ~ ~ ~ #medusa:gaze_passable if score @s md_ray matches ..23 positioned ^ ^ ^0.5 run function medusa:staff/spikes/ground_ray
