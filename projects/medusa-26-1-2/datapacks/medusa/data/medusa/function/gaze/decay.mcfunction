scoreboard players add @s md_decay 1
execute if score @s md_decay matches 5.. run scoreboard players remove @s md_petr 20
execute if score @s md_petr matches ..-1 run scoreboard players set @s md_petr 0
