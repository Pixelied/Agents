execute unless score @s md_p1_done matches 1 positioned ~-11 ~-17 ~29 as @a[dx=19,dy=7,dz=12] run title @s actionbar {"text":"Averted Eyes — Turn each sentinel away from the Gorgon.","color":"gray"}
execute if block ~-8 ~-17 ~35 minecraft:stone_button[powered=true] if score @s md_p1_b1 matches 0 run function medusa:puzzle/averted_eyes/press_1
execute if block ~-4 ~-17 ~35 minecraft:stone_button[powered=true] if score @s md_p1_b2 matches 0 run function medusa:puzzle/averted_eyes/press_2
execute if block ~0 ~-17 ~35 minecraft:stone_button[powered=true] if score @s md_p1_b3 matches 0 run function medusa:puzzle/averted_eyes/press_3
execute if block ~-4 ~-17 ~39 minecraft:stone_button[powered=true] if score @s md_p1_submit matches 0 run function medusa:puzzle/averted_eyes/submit
execute if block ~-8 ~-17 ~35 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b1 0
execute if block ~-4 ~-17 ~35 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b2 0
execute if block ~0 ~-17 ~35 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_b3 0
execute if block ~-4 ~-17 ~39 minecraft:stone_button[powered=false] run scoreboard players set @s md_p1_submit 0
