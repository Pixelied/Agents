scoreboard players add @s md_mtick 1
execute if score @s md_mtick matches 20 run playsound minecraft:block.stone.break master @a[distance=..128] ~-2 ~-6 ~72 0.9 0.55
execute if score @s md_mtick matches 20 run particle minecraft:smoke ~-23 ~-6 ~51 10 2 10 0.02 25 force
execute if score @s md_mtick matches 40 run playsound minecraft:block.deepslate.break master @a[distance=..128] ~-2 ~-6 ~72 1.2 0.4
execute if score @s md_mtick matches 40 run particle minecraft:smoke ~19 ~-6 ~93 14 2 14 0.03 35 force
execute if score @s md_mtick matches 55 run playsound minecraft:block.iron_door.close master @a[distance=..128] ~-2 ~-6 ~72 0.8 0.5
execute if score @s md_mtick matches 60.. run function medusa:maze/transition/start_open
