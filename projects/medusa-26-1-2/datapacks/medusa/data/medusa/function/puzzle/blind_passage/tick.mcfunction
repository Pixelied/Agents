scoreboard players add @s md_p3_timer 1
execute if score @s md_p3_timer matches 20.. run scoreboard players set @s md_p3_timer 0
execute if score @s md_p3_timer matches 0 run scoreboard players add @s md_p3_zone 1
execute if score @s md_p3_zone matches 4.. run scoreboard players set @s md_p3_zone 0
execute if score @s md_p3_zone matches 0 positioned ~-14 ~-17 ~60 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 1 positioned ~-10 ~-17 ~60 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 2 positioned ~-14 ~-17 ~64 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute if score @s md_p3_zone matches 3 positioned ~-10 ~-17 ~64 as @a[dx=3,dy=3,dz=3] run function medusa:puzzle/blind_passage/caught
execute positioned ~-6 ~-17 ~60 if entity @a[dx=3,dy=3,dz=3] run scoreboard players set @s md_p3_done 1
