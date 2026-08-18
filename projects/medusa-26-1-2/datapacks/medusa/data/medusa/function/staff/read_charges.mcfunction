scoreboard players set @s md_staff 0
execute store result score @s md_staff run data get entity @s SelectedItem.components."minecraft:custom_data".charges 1
execute if score @s md_staff matches 65.. run scoreboard players set @s md_staff 64
