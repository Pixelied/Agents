scoreboard players set @s md_p1_b2 1
scoreboard players add @s md_p1_o2 1
execute if score @s md_p1_o2 matches 4.. run scoreboard players set @s md_p1_o2 0
execute if score @s md_p1_o2 matches 0 run setblock ~-4 ~-16 ~33 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o2 matches 1 run setblock ~-4 ~-16 ~33 minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o2 matches 2 run setblock ~-4 ~-16 ~33 minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o2 matches 3 run setblock ~-4 ~-16 ~33 minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]
playsound minecraft:block.stone_button.click_on block @a[distance=..12] ~-4 ~-16 ~33 0.7 0.8
