$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run fill ~53 ~-17 ~61 ~55 ~-10 ~63 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run fill ~73 ~-17 ~81 ~75 ~-10 ~83 minecraft:air
$scoreboard players set @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_phase 2
