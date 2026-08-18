execute store result storage medusa:macro stone.aid int 1 run scoreboard players get @s md_aid
execute if score @s md_shell matches 4..7 run function medusa:petrify/statue/stage_cracked with storage medusa:macro stone
execute if score @s md_shell matches 8..11 run function medusa:petrify/statue/stage_shattered with storage medusa:macro stone
execute if score @s md_shell matches 12.. run function medusa:petrify/break_free
