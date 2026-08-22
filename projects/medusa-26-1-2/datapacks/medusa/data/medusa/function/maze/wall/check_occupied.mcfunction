scoreboard players set @s md_tmp 0
execute if score @s md_morient matches 1 positioned ~3 ~1 ~-1 if entity @a[gamemode=survival,dx=1,dy=6,dz=2] run scoreboard players set @s md_tmp 1
execute if score @s md_morient matches 1 positioned ~3 ~1 ~-1 if entity @a[gamemode=adventure,dx=1,dy=6,dz=2] run scoreboard players set @s md_tmp 1
execute if score @s md_morient matches 2 positioned ~-1 ~1 ~3 if entity @a[gamemode=survival,dx=2,dy=6,dz=1] run scoreboard players set @s md_tmp 1
execute if score @s md_morient matches 2 positioned ~-1 ~1 ~3 if entity @a[gamemode=adventure,dx=2,dy=6,dz=1] run scoreboard players set @s md_tmp 1
# Exact-runtime debug probe uses the same sweep volume without weakening normal player-only collision safety.
execute if score @s md_morient matches 1 positioned ~3 ~1 ~-1 if entity @e[type=minecraft:marker,tag=md.maze.occupancy_probe,dx=1,dy=6,dz=2] run scoreboard players set @s md_tmp 1
execute if score @s md_morient matches 2 positioned ~-1 ~1 ~3 if entity @e[type=minecraft:marker,tag=md.maze.occupancy_probe,dx=2,dy=6,dz=1] run scoreboard players set @s md_tmp 1
