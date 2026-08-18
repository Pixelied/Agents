scoreboard players add @s md_ray 1
execute if block ~ ~ ~ #medusa:gaze_passable as @e[tag=!md.staff_caster,type=!minecraft:marker,type=!minecraft:item,type=!minecraft:item_display,type=!minecraft:block_display,type=!minecraft:text_display,type=!minecraft:interaction,type=!minecraft:armor_stand,type=!minecraft:experience_orb,distance=..0.8,limit=1,sort=nearest] run function medusa:staff/target/register_id
execute if entity @e[tag=md.staff_candidate,distance=..0.8,limit=1,sort=nearest] run function medusa:staff/target/acquire_candidate
execute if score @s md_staff_hit matches 0 if block ~ ~ ~ #medusa:gaze_passable if score @s md_ray matches ..31 positioned ^ ^ ^0.5 run function medusa:staff/target/raycast
