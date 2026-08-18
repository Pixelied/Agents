effect clear @s minecraft:resistance
damage @s 2 medusa:petrification
effect give @s minecraft:resistance 2 4 true
scoreboard players set @s md_stone_timer 0
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.25 0.6 0.25 0.02 8 force
