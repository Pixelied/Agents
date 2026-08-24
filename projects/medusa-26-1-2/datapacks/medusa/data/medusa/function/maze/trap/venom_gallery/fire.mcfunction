particle minecraft:smoke ~ ~1 ~ 4.0 0.3 0.8 0.02 45 force
execute positioned ~-4 ~ ~-1 as @a[gamemode=survival,dx=8,dy=3,dz=2] run effect give @s minecraft:poison 4 0 true
execute positioned ~-4 ~ ~-1 as @a[gamemode=adventure,dx=8,dy=3,dz=2] run effect give @s minecraft:poison 4 0 true
