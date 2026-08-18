# Clear transient player immobilization/channel state on every data-pack reload.
execute as @a[tag=md.participant] run function medusa:instance/participants/clear_player
# Interrupted awakening/active/death states recover to a clean sealed retry.
execute as @e[type=minecraft:marker,tag=md.instance,scores={md_state=1..3}] at @s run function medusa:dungeon/restore_cover
scoreboard players set @e[type=minecraft:marker,tag=md.instance,scores={md_state=1..3}] md_state 0
scoreboard players set @e[type=minecraft:marker,tag=md.instance,scores={md_state=1..3}] md_phase 0
