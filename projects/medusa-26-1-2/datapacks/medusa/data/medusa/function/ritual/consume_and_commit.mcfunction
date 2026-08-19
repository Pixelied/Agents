clear @s minecraft:turtle_scute[minecraft:custom_data~{md_item:"gorgon_scale"}] 4
clear @s minecraft:flint[minecraft:custom_data~{md_item:"serpent_fang"}] 1
clear @s minecraft:player_head[minecraft:custom_data~{md_item:"golden_gorgon_eye"}] 1
$scoreboard players set @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_ritual_paid 1
$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run function medusa:ritual/commit
