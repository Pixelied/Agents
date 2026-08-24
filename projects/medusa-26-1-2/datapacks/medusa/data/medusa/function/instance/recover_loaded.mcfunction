execute as @a[tag=md.participant] run function medusa:instance/participants/clear_player
execute as @e[type=minecraft:marker,tag=md.instance,scores={md_state=1..3}] at @s run function medusa:arena/reset/start
execute as @e[type=minecraft:marker,tag=md.instance] at @s run function medusa:instance/recover_one
