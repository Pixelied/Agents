scoreboard players add @s md_manim 1
execute if score @s md_morient matches 1 if score @s md_manim matches 8 run fill ~3 ~1 ~-1 ~4 ~1 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 16 run fill ~3 ~2 ~-1 ~4 ~2 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 24 run fill ~3 ~3 ~-1 ~4 ~3 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 32 run fill ~3 ~4 ~-1 ~4 ~4 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 40 run fill ~3 ~5 ~-1 ~4 ~5 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 48 run fill ~3 ~6 ~-1 ~4 ~6 ~1 minecraft:air
execute if score @s md_morient matches 1 if score @s md_manim matches 56 run fill ~3 ~7 ~-1 ~4 ~7 ~1 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 8 run fill ~-1 ~1 ~3 ~1 ~1 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 16 run fill ~-1 ~2 ~3 ~1 ~2 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 24 run fill ~-1 ~3 ~3 ~1 ~3 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 32 run fill ~-1 ~4 ~3 ~1 ~4 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 40 run fill ~-1 ~5 ~3 ~1 ~5 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 48 run fill ~-1 ~6 ~3 ~1 ~6 ~4 minecraft:air
execute if score @s md_morient matches 2 if score @s md_manim matches 56 run fill ~-1 ~7 ~3 ~1 ~7 ~4 minecraft:air
execute if score @s md_manim matches 64.. run function medusa:maze/wall/finish
