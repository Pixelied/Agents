scoreboard players add @s md_timer 1
execute if score @s md_timer matches 20 run playsound minecraft:block.stone.break master @a[distance=..48] ~64 ~-12 ~72 1.2 0.7
execute if score @s md_timer matches 20 run fill ~62 ~-10 ~70 ~62 ~-8 ~74 minecraft:air
execute if score @s md_timer matches 40 run playsound minecraft:block.deepslate.break master @a[distance=..48] ~64 ~-12 ~72 1.4 0.6
execute if score @s md_timer matches 40 run fill ~66 ~-10 ~70 ~66 ~-8 ~74 minecraft:air
execute if score @s md_timer matches 60 run particle minecraft:explosion ~64 ~-11 ~72 1 2 1 0 4 force
execute if score @s md_timer matches 60 run fill ~63 ~-10 ~70 ~65 ~-9 ~74 minecraft:air
execute if score @s md_timer matches 80 run playsound minecraft:entity.ender_dragon.growl master @a[distance=..48] ~64 ~-12 ~72 0.7 0.8
execute if score @s md_timer matches 80 run fill ~62 ~-17 ~70 ~66 ~-11 ~74 minecraft:air
execute if score @s md_timer matches 100.. run function medusa:arena/awakening/finish
