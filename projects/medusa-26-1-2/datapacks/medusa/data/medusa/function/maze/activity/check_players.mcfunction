scoreboard players set @s md_tmp 0
execute positioned ~-47 ~-18 ~27 if entity @a[gamemode=survival,dx=90,dy=17,dz=90] run scoreboard players set @s md_tmp 1
execute positioned ~-47 ~-18 ~27 if entity @a[gamemode=adventure,dx=90,dy=17,dz=90] run scoreboard players set @s md_tmp 1
