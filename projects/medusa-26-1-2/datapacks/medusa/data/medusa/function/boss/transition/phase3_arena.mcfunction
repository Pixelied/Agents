$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run fill ~53 ~-17 ~81 ~55 ~-10 ~83 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run fill ~73 ~-17 ~61 ~75 ~-10 ~63 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] at @s run fill ~63 ~-17 ~70 ~65 ~-15 ~74 minecraft:air
$scoreboard players set @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_phase 3
