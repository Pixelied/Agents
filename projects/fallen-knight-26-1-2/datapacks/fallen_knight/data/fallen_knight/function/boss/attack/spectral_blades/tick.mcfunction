scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 35.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 35.. run data merge entity @s {NoAI:0b}
