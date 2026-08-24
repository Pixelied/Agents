tag @s add md.petrified
tag @s remove md.full_petrify_pending
scoreboard players set @s md_petr 1000
scoreboard players set @s md_shell 0
scoreboard players set @s md_stone_timer 0
scoreboard players set @s md_decay 0
function medusa:petrify/statue/spawn
playsound minecraft:block.stone.place master @s ~ ~ ~ 1 0.55
title @s actionbar {"text":"PETRIFIED — teammates can break the stone!","color":"gray","bold":true}
