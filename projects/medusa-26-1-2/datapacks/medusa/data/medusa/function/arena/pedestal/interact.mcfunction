advancement revoke @s only medusa:events/pedestal_interact
tag @s add md.eye_interactor
execute as @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..5,limit=1,sort=nearest] at @s run function medusa:arena/pedestal/interact_entity
tag @s remove md.eye_interactor
