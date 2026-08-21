tag @s add fk.wave
execute store result score @s fk_aid run data get storage fallen_knight:macro helper.aid 1
scoreboard players set @s fk_timer 0
data modify entity @s Rotation set from storage fallen_knight:macro helper.rotation
