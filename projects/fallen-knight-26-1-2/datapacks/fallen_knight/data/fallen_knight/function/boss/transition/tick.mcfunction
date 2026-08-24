scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..14 run particle minecraft:smoke ~ ~1 ~ 0.12 0.2 0.12 0.01 2 force @a[distance=..24]
execute if score @s fk_timer matches 15 run playsound minecraft:item.shield.break hostile @a[distance=..28] ~ ~ ~ 1 0.7
execute if score @s fk_timer matches 15 run particle minecraft:poof ~ ~1 ~ 0.35 0.5 0.35 0.08 18 force @a[distance=..28]
execute if score @s fk_timer matches 15 run item replace entity @s weapon.offhand with minecraft:air
execute if score @s fk_timer matches 16 run item replace entity @s weapon.mainhand with minecraft:netherite_sword
execute if score @s fk_timer matches 16..29 run particle minecraft:witch ~ ~1 ~ 0.18 0.35 0.18 0.01 2 force @a[distance=..24]
execute if score @s fk_timer matches 30 run function fallen_knight:boss/transition/rename_bar
execute if score @s fk_timer matches 31.. run function fallen_knight:boss/transition/finish
