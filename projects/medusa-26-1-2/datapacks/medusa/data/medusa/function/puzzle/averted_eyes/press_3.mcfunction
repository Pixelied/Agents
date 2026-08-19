scoreboard players set @s md_p1_b3 1
scoreboard players add @s md_p1_o3 1
execute if score @s md_p1_o3 matches 4.. run scoreboard players set @s md_p1_o3 0
execute if score @s md_p1_o3 matches 0 run setblock ~0 ~-16 ~33 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o3 matches 1 run setblock ~0 ~-16 ~33 minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o3 matches 2 run setblock ~0 ~-16 ~33 minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]
execute if score @s md_p1_o3 matches 3 run setblock ~0 ~-16 ~33 minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]
playsound minecraft:block.stone_button.click_on block @a[distance=..12] ~0 ~-16 ~33 0.7 0.8
