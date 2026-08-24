execute as @e[type=minecraft:marker,tag=fk.debug_arena,tag=fk.arena] at @s run function fallen_knight:debug/cleanup_test_arena
kill @e[type=minecraft:marker,tag=fk.debug_arena,tag=fk.arena_seed]
tag @s remove fk.participant
scoreboard players set @s fk_aid 0
scoreboard players set @s fk_eid 0
scoreboard players set @s fk_ptime 0
