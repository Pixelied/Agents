execute as @e[type=minecraft:marker,tag=fk.arena_seed] at @s run function fallen_knight:arena/register_seed
execute as @e[type=minecraft:marker,tag=fk.arena] at @s run function fallen_knight:arena/tick_one
