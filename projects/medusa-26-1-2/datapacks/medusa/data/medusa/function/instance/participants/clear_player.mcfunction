function medusa:petrify/clear_player
tag @s remove md.participant
tag @s remove md.petrified
tag @s remove md.staff_channel
scoreboard players set @s md_eid 0
scoreboard players set @s md_petr 0
scoreboard players set @s md_pct 0
scoreboard players set @s md_decay 0
scoreboard players set @s md_shell 0
scoreboard players set @s md_grace 0
scoreboard players set @s md_lock 0
scoreboard players set @s md_use 0
