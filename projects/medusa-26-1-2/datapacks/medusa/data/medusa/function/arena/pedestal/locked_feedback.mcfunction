execute positioned ~64 ~-16 ~66 as @a[tag=md.eye_interactor,distance=..6] run tellraw @s {"text":"The Golden Gorgon Eye is sealed. Complete the three trials to break its binding.","color":"gray"}
execute positioned ~64 ~-16 ~66 as @a[tag=md.eye_interactor,distance=..6] at @s run playsound minecraft:block.enchantment_table.use master @s ~ ~ ~ 0.5 0.65
