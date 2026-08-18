execute store result storage medusa:macro stone.aid int 1 run scoreboard players get @s md_aid
function medusa:petrify/statue/clear_helpers with storage medusa:macro stone
tag @s remove md.petrified
tag @s remove md.full_petrify_pending
scoreboard players set @s md_shell 0
scoreboard players set @s md_stone_timer 0
scoreboard players set @s md_petr 150
scoreboard players set @s md_decay 0
scoreboard players set @s md_grace 40
effect clear @s minecraft:resistance
effect clear @s minecraft:slowness
effect clear @s minecraft:weakness
effect clear @s minecraft:mining_fatigue
effect clear @s minecraft:invisibility
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.7 1.0 0.7 0.12 60 force
playsound minecraft:block.stone.break master @a[distance=..16] ~ ~ ~ 1.2 1.1
