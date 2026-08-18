execute if block ~-5 ~-17 ~33 minecraft:stone_button[powered=true] if score @s md_p1_b1 matches 0 run function medusa:puzzle/averted_eyes/press_1
execute if block ~-4 ~-17 ~33 minecraft:stone_button[powered=true] if score @s md_p1_b2 matches 0 run function medusa:puzzle/averted_eyes/press_2
execute if block ~-3 ~-17 ~33 minecraft:stone_button[powered=true] if score @s md_p1_b3 matches 0 run function medusa:puzzle/averted_eyes/press_3
execute if block ~-5 ~-17 ~34 minecraft:stone_button[powered=true] if score @s md_p1_submit matches 0 run function medusa:puzzle/averted_eyes/submit
execute if block ~-5 ~-17 ~33 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b1 0
execute if block ~-4 ~-17 ~33 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b2 0
execute if block ~-3 ~-17 ~33 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b3 0
execute if block ~-5 ~-17 ~34 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_submit 0
