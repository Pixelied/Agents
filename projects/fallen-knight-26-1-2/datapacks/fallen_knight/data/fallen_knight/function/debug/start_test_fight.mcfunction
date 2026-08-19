function fallen_knight:debug/cleanup_test_fights
summon minecraft:marker ~ ~ ~ {Tags:["fk.arena_seed","fk.debug_arena","fk.debug_primary"]}
execute as @e[type=minecraft:marker,tag=fk.debug_primary,tag=fk.arena_seed,sort=nearest,limit=1,distance=..2] at @s run function fallen_knight:arena/register_seed
execute as @e[type=minecraft:marker,tag=fk.debug_primary,tag=fk.arena,sort=nearest,limit=1,distance=..2] at @s run function fallen_knight:arena/start
tellraw @s [{"text":"[Fallen Knight] ","color":"dark_gray"},{"text":"Test fight started. Stay within the arena; use /function fallen_knight:debug/cleanup_test_fights to reset.","color":"gray"}]
