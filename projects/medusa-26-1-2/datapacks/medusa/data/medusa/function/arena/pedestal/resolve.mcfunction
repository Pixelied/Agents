$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s if score @s md_state matches 0 if score @s md_dungeon_clear matches 1 run function medusa:arena/pedestal/take_first_eye
$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s if score @s md_state matches 4 run function medusa:ritual/pedestal_interact
