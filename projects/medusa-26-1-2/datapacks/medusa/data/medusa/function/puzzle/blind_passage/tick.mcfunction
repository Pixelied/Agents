execute unless score @s md_p3_done matches 1 positioned ~-17 ~-17 ~58 as @a[dx=14,dy=7,dz=10] run title @s actionbar {"text":"Blind Passage — Cross when the watchers are dark; lit eyes petrify.","color":"gray"}
scoreboard players add @s md_p3_timer 1
execute if score @s md_p3_timer matches 20.. run scoreboard players set @s md_p3_timer 0
execute if score @s md_p3_timer matches 0 run scoreboard players add @s md_p3_zone 1
execute if score @s md_p3_zone matches 4.. run scoreboard players set @s md_p3_zone 0
# Visible watcher lamps mirror the currently dangerous quadrant.
setblock ~-13 ~-15 ~60 minecraft:redstone_lamp[lit=false]
setblock ~-9 ~-15 ~60 minecraft:redstone_lamp[lit=false]
setblock ~-13 ~-15 ~64 minecraft:redstone_lamp[lit=false]
setblock ~-9 ~-15 ~64 minecraft:redstone_lamp[lit=false]
execute if score @s md_p3_zone matches 0 run setblock ~-13 ~-15 ~60 minecraft:redstone_lamp[lit=true]
execute if score @s md_p3_zone matches 1 run setblock ~-9 ~-15 ~60 minecraft:redstone_lamp[lit=true]
execute if score @s md_p3_zone matches 2 run setblock ~-13 ~-15 ~64 minecraft:redstone_lamp[lit=true]
execute if score @s md_p3_zone matches 3 run setblock ~-9 ~-15 ~64 minecraft:redstone_lamp[lit=true]
execute if score @s md_p3_zone matches 0 positioned ~-14 ~-17 ~60 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 1 positioned ~-10 ~-17 ~60 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 2 positioned ~-14 ~-17 ~64 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 3 positioned ~-10 ~-17 ~64 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute positioned ~-6 ~-17 ~60 if entity @a[dx=3,dy=3,dz=3] run scoreboard players set @s md_p3_done 1
execute if score @s md_p3_done matches 1 if block ~-2 ~-17 ~62 minecraft:iron_bars run playsound minecraft:block.iron_door.open master @a[distance=..24] ~-2 ~-15 ~62 0.8 0.9
execute if score @s md_p3_done matches 1 run fill ~-2 ~-17 ~60 ~-2 ~-13 ~64 minecraft:air
