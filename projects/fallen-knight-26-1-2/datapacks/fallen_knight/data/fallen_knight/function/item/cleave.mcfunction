scoreboard players set @s fk_cleave 100
tag @s add fk.cleave_source
execute at @s positioned ^ ^1 ^1.75 as @e[type=#fallen_knight:cursed_cleave_targets,tag=!fk.boss,distance=..2.5,limit=6,sort=nearest] run damage @s 4 fallen_knight:cursed_magic by @a[tag=fk.cleave_source,limit=1]
particle minecraft:sweep_attack ^ ^1 ^1.5 0.6 0.2 0.6 0 4 force
playsound minecraft:entity.player.attack.sweep player @s ~ ~ ~ 0.8 0.75
tag @s remove fk.cleave_source
