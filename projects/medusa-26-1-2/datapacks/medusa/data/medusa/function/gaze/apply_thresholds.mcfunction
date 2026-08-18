execute if score @s md_petr matches 400..699 run effect give @s minecraft:slowness 1 0 true
execute if score @s md_petr matches 700..899 run effect give @s minecraft:slowness 1 2 true
execute if score @s md_petr matches 700..899 run effect give @s minecraft:weakness 1 0 true
execute if score @s md_petr matches 900..999 run effect give @s minecraft:slowness 1 5 true
execute if score @s md_petr matches 900..999 run effect give @s minecraft:weakness 1 1 true
execute if score @s md_petr matches 900..999 run playsound minecraft:block.stone.hit master @s ~ ~ ~ 0.35 0.55
execute if score @s md_petr matches 1000.. unless entity @s[tag=md.petrified] run function medusa:petrify/enter_full
