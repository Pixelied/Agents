function fallen_knight:debug/cleanup_test_fights
summon minecraft:marker ~ ~ ~
tag @e[type=minecraft:marker,tag=!fk.debug_arena,sort=nearest,limit=1,distance=..1] add fk.debug_arena
tag @e[type=minecraft:marker,tag=fk.debug_arena,tag=!fk.arena_seed,sort=nearest,limit=1,distance=..1] add fk.arena_seed
summon minecraft:marker ~40 ~ ~
execute positioned ~40 ~ ~ run tag @e[type=minecraft:marker,tag=!fk.debug_arena,sort=nearest,limit=1,distance=..1] add fk.debug_arena
execute positioned ~40 ~ ~ run tag @e[type=minecraft:marker,tag=fk.debug_arena,tag=!fk.arena_seed,sort=nearest,limit=1,distance=..1] add fk.arena_seed
