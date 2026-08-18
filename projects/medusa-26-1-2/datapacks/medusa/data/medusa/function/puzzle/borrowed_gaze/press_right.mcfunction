scoreboard players set @s md_p2_br 1
scoreboard players add @s md_p2_right 1
execute if score @s md_p2_right matches 4.. run scoreboard players set @s md_p2_right 0
execute if score @s md_p2_right matches 0 run setblock ~-24 ~-16 ~45 minecraft:lightning_rod[facing=north,waterlogged=false]
execute if score @s md_p2_right matches 1 run setblock ~-24 ~-16 ~45 minecraft:lightning_rod[facing=east,waterlogged=false]
execute if score @s md_p2_right matches 2 run setblock ~-24 ~-16 ~45 minecraft:lightning_rod[facing=south,waterlogged=false]
execute if score @s md_p2_right matches 3 run setblock ~-24 ~-16 ~45 minecraft:lightning_rod[facing=west,waterlogged=false]
