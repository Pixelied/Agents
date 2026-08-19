scoreboard players set @s md_p1_submit 1
execute if score @s md_p1_o1 matches 1 if score @s md_p1_o2 matches 3 if score @s md_p1_o3 matches 2 run scoreboard players set @s md_p1_done 1
execute if score @s md_p1_done matches 1 run playsound minecraft:block.beacon.activate master @a[distance=..24] ~-4 ~-15 ~38 0.9 1.35
execute if score @s md_p1_done matches 1 run particle minecraft:totem_of_undying ~-4 ~-14 ~38 1.5 1.0 1.5 0.05 24 force
execute if score @s md_p1_done matches 1 run fill ~3 ~-17 ~41 ~7 ~-13 ~41 minecraft:air
execute unless score @s md_p1_done matches 1 run particle minecraft:angry_villager ~-4 ~-15 ~38 1.1 0.7 1.1 0 12 force
execute unless score @s md_p1_done matches 1 run summon minecraft:silverfish ~-7 ~-17 ~37 {Tags:["md.snake","md.puzzle_snake","md.new_puzzle_snake"],PersistenceRequired:1b}
execute unless score @s md_p1_done matches 1 store result score @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..6,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..6] remove md.new_puzzle_snake
execute unless score @s md_p1_done matches 1 run summon minecraft:silverfish ~-1 ~-17 ~37 {Tags:["md.snake","md.puzzle_snake","md.new_puzzle_snake"],PersistenceRequired:1b}
execute unless score @s md_p1_done matches 1 store result score @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..6,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..6] remove md.new_puzzle_snake
