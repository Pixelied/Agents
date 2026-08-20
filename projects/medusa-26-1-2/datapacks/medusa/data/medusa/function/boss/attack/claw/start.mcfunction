scoreboard players set @s md_attack 1
scoreboard players set @s md_atk_timer 0
playsound minecraft:entity.player.attack.sweep block @a[distance=..24] ~ ~ ~ 0.9 0.8
particle minecraft:sweep_attack ^ ^1 ^1.2 0.2 0.4 0.2 0 2 force
