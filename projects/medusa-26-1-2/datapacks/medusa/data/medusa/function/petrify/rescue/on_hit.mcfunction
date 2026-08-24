advancement revoke @s only medusa:events/player_hurt_entity
tag @s add md.rescuer
execute as @e[type=minecraft:interaction,tag=md.statue_hitbox,distance=..5,limit=1,sort=nearest] run function medusa:petrify/rescue/resolve_hitbox
tag @s remove md.rescuer
