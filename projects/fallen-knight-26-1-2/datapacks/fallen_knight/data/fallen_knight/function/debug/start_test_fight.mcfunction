function fallen_knight:debug/cleanup_test_fights
summon minecraft:marker ~ ~ ~
tag @e[type=minecraft:marker,tag=!fk.debug_arena,sort=nearest,limit=1,distance=..1] add fk.debug_arena
tag @e[type=minecraft:marker,tag=fk.debug_arena,tag=!fk.debug_primary,sort=nearest,limit=1,distance=..1] add fk.debug_primary
tag @e[type=minecraft:marker,tag=fk.debug_primary,sort=nearest,limit=1,distance=..1] add fk.arena_seed
execute as @e[type=minecraft:marker,tag=fk.debug_primary,tag=fk.arena_seed,sort=nearest,limit=1,distance=..2] at @s run function fallen_knight:arena/register_seed
execute as @e[type=minecraft:marker,tag=fk.debug_primary,tag=fk.arena,sort=nearest,limit=1,distance=..2] at @s run function fallen_knight:arena/start
tellraw @s [{"text":"[Fallen Knight] ","color":"dark_gray"},{"text":"Test fight started. Stay within the arena; use /function fallen_knight:debug/cleanup_test_fights to reset.","color":"gray"}]
