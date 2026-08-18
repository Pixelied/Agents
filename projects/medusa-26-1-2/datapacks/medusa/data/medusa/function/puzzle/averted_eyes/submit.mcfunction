scoreboard players set @s md_p1_submit 1
execute if score @s md_p1_o1 matches 1 if score @s md_p1_o2 matches 3 if score @s md_p1_o3 matches 2 run scoreboard players set @s md_p1_done 1
execute if score @s md_p1_done matches 1 run playsound minecraft:block.beacon.activate master @a[distance=..18] ~-4 ~-16 ~33 0.8 1.4
execute unless score @s md_p1_done matches 1 run particle minecraft:angry_villager ~-4 ~-15 ~33 0.8 0.5 0.8 0 12 force
execute unless score @s md_p1_done matches 1 run summon minecraft:silverfish ~-6 ~-17 ~34 {Tags:["md.snake","md.puzzle_snake","md.new_puzzle_snake"],PersistenceRequired:1b}
execute unless score @s md_p1_done matches 1 store result score @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..5,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..5] remove md.new_puzzle_snake
execute unless score @s md_p1_done matches 1 run summon minecraft:silverfish ~-2 ~-17 ~34 {Tags:["md.snake","md.puzzle_snake","md.new_puzzle_snake"],PersistenceRequired:1b}
execute unless score @s md_p1_done matches 1 store result score @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..5,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:silverfish,tag=md.new_puzzle_snake,distance=..5] remove md.new_puzzle_snake
