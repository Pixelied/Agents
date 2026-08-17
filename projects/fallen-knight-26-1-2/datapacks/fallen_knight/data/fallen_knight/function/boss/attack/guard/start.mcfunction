scoreboard players set @s fk_attack 1
scoreboard players set @s fk_prev 1
scoreboard players set @s fk_timer 0
scoreboard players set @s fk_cd_guard 80
data merge entity @s {NoAI:1b}
item replace entity @s weapon.offhand with minecraft:shield
