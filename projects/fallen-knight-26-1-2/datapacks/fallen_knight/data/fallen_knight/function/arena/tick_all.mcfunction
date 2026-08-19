execute as @e[type=minecraft:marker,tag=!fk.arena,tag=!fk.arena_seed,tag=!fk.wave,tag=!fk.spectral] at @s if block ~ ~-1 ~ minecraft:lodestone if block ~1 ~-1 ~ minecraft:red_terracotta if block ~-1 ~-1 ~ minecraft:blackstone run tag @s add fk.arena_seed
execute as @e[type=minecraft:marker,tag=fk.arena_seed] at @s run function fallen_knight:arena/register_seed
execute as @e[type=minecraft:marker,tag=fk.arena] at @s run function fallen_knight:arena/tick_one
