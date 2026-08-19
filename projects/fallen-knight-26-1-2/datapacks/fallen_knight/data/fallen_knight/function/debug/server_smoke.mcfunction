# Stage 1: create the arena and carrier using execute-summon executor context.
kill @e[tag=fk.server_smoke]
execute summon minecraft:marker run function fallen_knight:debug/server_smoke_arena_setup
execute positioned ~ ~1 ~ summon minecraft:vindicator run function fallen_knight:debug/server_smoke_boss_setup
