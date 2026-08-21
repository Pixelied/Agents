execute store result storage fallen_knight:macro helper.aid int 1 run scoreboard players get @s fk_aid
data modify storage fallen_knight:macro helper.rotation set from entity @s Rotation
execute positioned ^ ^0.2 ^1.5 summon minecraft:marker run function fallen_knight:boss/attack/cursed_slash/bootstrap_wave
playsound minecraft:entity.evoker.cast_spell hostile @a[distance=..24] ~ ~ ~ 0.7 1.35
