execute store result score @s md_hp run data get entity @s Health 1
execute if score @s md_hp matches ..0 unless entity @s[tag=md.death_started] run function medusa:boss/death/start
execute if score @s md_hp matches 1.. unless entity @s[tag=md.staff_petrified] run function medusa:boss/alive_tick
