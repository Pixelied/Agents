execute if score @s md_grace matches 1.. run scoreboard players remove @s md_grace 1
execute if entity @s[tag=md.petrified] run effect give @s minecraft:resistance 1 4 true
execute if entity @s[tag=md.petrified] run effect give @s minecraft:slowness 1 255 true
execute if entity @s[tag=md.petrified] run effect give @s minecraft:weakness 1 255 true
execute if entity @s[tag=md.petrified] run effect give @s minecraft:mining_fatigue 1 255 true
execute if entity @s[tag=md.petrified] run effect give @s minecraft:invisibility 1 0 true
execute if entity @s[tag=md.petrified] store result storage medusa:macro stone.aid int 1 run scoreboard players get @s md_aid
execute if entity @s[tag=md.petrified] run function medusa:petrify/freeze_to_anchor with storage medusa:macro stone
execute if entity @s[tag=md.petrified] run scoreboard players add @s md_stone_timer 1
execute if entity @s[tag=md.petrified] if score @s md_stone_timer matches 20.. run function medusa:petrify/suffocate
